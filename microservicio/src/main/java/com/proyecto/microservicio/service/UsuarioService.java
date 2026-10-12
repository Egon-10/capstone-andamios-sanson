package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;

import com.proyecto.microservicio.dto.CambioEstadoRequest;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.dto.PerfilResponse;
import com.proyecto.microservicio.dto.UsuarioActualizacionRequest;
import com.proyecto.microservicio.dto.UsuarioOpcion;
import com.proyecto.microservicio.dto.UsuarioRegistroRequest;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Roles;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AccesoRepository;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.RefreshTokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * HU-43: registro de usuarios con validación de duplicados.
 * HU-07: edición parcial de datos de usuario.
 * HU-30: listado con búsqueda, filtros y paginación.
 * HU-31: activación y desactivación con efecto inmediato sobre las sesiones.
 * HU-34: perfil propio.
 * HU-05: las operaciones de administración exigen el rol ADMINISTRADOR también
 * a nivel de método, además de la regla por endpoint.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final RolRepository rolRepository;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final AccesoRepository accesos;
    private final RefreshTokenService refreshTokens;

    /** Campos de la entidad por los que se puede ordenar el listado. */
    private static final String ORDEN_POR_OMISION = "nombre";
    private static final Set<String> ORDENABLES =
            Set.of(ORDEN_POR_OMISION, "apellidos", "nombreUsuario", "fechaCreacion", "estado");

    public UsuarioService(UsuarioRepository repository, RolRepository rolRepository,
                          PasswordEncoder encoder, AuditoriaService auditoria,
                          AccesoRepository accesos, RefreshTokenService refreshTokens) {
        this.repository = repository;
        this.rolRepository = rolRepository;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.accesos = accesos;
        this.refreshTokens = refreshTokens;
    }

    /** HU-30: búsqueda paginada. El orden por omisión es alfabético por nombre. */
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Page<Usuario> buscar(String texto, Long rolId, String estado, int pagina, int tamano,
                                String orden, boolean descendente) {
        String campo = orden != null && ORDENABLES.contains(orden) ? orden : ORDEN_POR_OMISION;
        Sort sort = Sort.by(descendente ? Sort.Direction.DESC : Sort.Direction.ASC, campo)
                .and(Sort.by("id"));
        return consultar(texto, rolId, estado,
                PageRequest.of(Math.max(pagina, 0), AuditoriaService.acotar(tamano), sort));
    }

    /**
     * Para el reporte de usuarios, que también puede emitir el gerente. Usa
     * el mismo filtro que la pantalla, con un tope propio en lugar del tamaño
     * máximo de página.
     */
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE')")
    @Transactional(readOnly = true)
    public Page<Usuario> buscarParaReporte(String texto, Long rolId, String estado, int tope) {
        return consultar(texto, rolId, estado, PageRequest.of(0, tope, Sort.by(ORDEN_POR_OMISION, "apellidos", "id")));
    }

    private Page<Usuario> consultar(String texto, Long rolId, String estado, PageRequest pagina) {
        String patron = new FiltroBitacora(texto, null, null, null, null).patronTexto();
        String filtroEstado = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase();
        if (filtroEstado != null && !Usuario.ESTADO_ACTIVO.equals(filtroEstado)
                && !Usuario.ESTADO_INACTIVO.equals(filtroEstado)) {
            throw new ReglaNegocioException("estado", "Estado no válido");
        }
        return repository.buscar(patron, rolId, filtroEstado, pagina);
    }

    /** Lista corta de usuarios para los filtros de las bitácoras. */
    @Transactional(readOnly = true)
    public List<UsuarioOpcion> opciones() {
        return repository.findAllByOrderByNombreAscApellidosAsc().stream().map(UsuarioOpcion::de).toList();
    }

    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.from(buscar(id));
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public UsuarioResponse registrar(UsuarioRegistroRequest s, Long creadorId) {
        if (!s.password().equals(s.confirmarPassword())) {
            throw new ReglaNegocioException("confirmarPassword", "La contraseña y su confirmación no coinciden");
        }
        validarDocumento(s.tipoDocumento(), s.numeroDocumento());

        String nombreUsuario = s.nombreUsuario().trim();
        String correo = s.correo().trim().toLowerCase();
        String documento = s.numeroDocumento().trim();

        if (repository.existsByNombreUsuario(nombreUsuario)) {
            throw new ConflictoException("nombreUsuario", "El nombre de usuario ya está registrado");
        }
        if (repository.existsByCorreoIgnoreCase(correo)) {
            throw new ConflictoException("correo", "El correo ya está registrado");
        }
        if (repository.existsByNumeroDocumento(documento)) {
            throw new ConflictoException("numeroDocumento", "El número de documento ya está registrado");
        }

        Usuario u = new Usuario();
        u.setNombre(s.nombres().trim());
        u.setApellidos(s.apellidos().trim());
        u.setTipoDocumento(s.tipoDocumento());
        u.setNumeroDocumento(documento);
        u.setCorreo(correo);
        u.setTelefono(vacioANulo(s.telefono()));
        u.setNombreUsuario(nombreUsuario);
        u.setPassword(encoder.encode(s.password()));
        u.setRol(buscarRol(s.rolId()));
        u.setArea(s.area());
        u.setTurno(vacioANulo(s.turno()));
        u.setEstado(Usuario.ESTADO_ACTIVO);
        u.setFechaCreacion(ZonaHoraria.ahora());

        Usuario guardado = repository.save(u);
        auditoria.registrar("CREAR USUARIO: " + guardado.getNombreUsuario(), creadorId);
        return UsuarioResponse.from(guardado);
    }

    /**
     * Edición parcial por un administrador: puede cambiar todos los campos editables,
     * incluidos el rol y el estado.
     */
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public UsuarioResponse actualizarParcial(Long id, UsuarioActualizacionRequest s, Long actorId) {
        Usuario u = buscar(id);
        aplicarCambiosComunes(u, s);
        if (s.rolId() != null) {
            Rol nuevo = buscarRol(s.rolId());
            if (!Roles.ADMINISTRADOR.equals(nuevo.getNombre())) {
                exigirOtroAdministrador(u, "No se puede quitar el rol de administrador al único administrador activo");
            }
            u.setRol(nuevo);
        }
        Usuario guardado = repository.save(u);
        auditoria.registrar("EDITAR USUARIO: " + etiqueta(guardado), actorId);
        if (s.estado() != null) {
            return aplicarCambioEstado(id, new CambioEstadoRequest(s.estado(), null), actorId);
        }
        return UsuarioResponse.from(guardado);
    }

    /**
     * HU-31: activa o desactiva una cuenta.
     *
     * Una cuenta desactivada no puede iniciar sesión y, además, pierde en el
     * acto las sesiones que tuviera abiertas: se revocan sus tokens de
     * renovación y se fija la hora desde la que sus tokens de acceso vuelven a
     * ser válidos. Sin esto, el usuario desactivado seguiría operando hasta
     * que su token venciera.
     *
     * No se puede desactivar la propia cuenta ni al último administrador
     * activo: cualquiera de las dos dejaría el sistema sin quien lo administre.
     */
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public UsuarioResponse cambiarEstado(Long id, CambioEstadoRequest s, Long actorId) {
        return aplicarCambioEstado(id, s, actorId);
    }

    private UsuarioResponse aplicarCambioEstado(Long id, CambioEstadoRequest s, Long actorId) {
        Usuario u = buscar(id);
        boolean desactivar = Usuario.ESTADO_INACTIVO.equals(s.estado());

        if (desactivar == !u.estaActivo()) {
            return UsuarioResponse.from(u);
        }
        if (desactivar) {
            if (id.equals(actorId)) {
                throw new ReglaNegocioException("estado", "No puede desactivar su propia cuenta");
            }
            exigirOtroAdministrador(u, "No se puede desactivar al único administrador activo");
            u.setEstado(Usuario.ESTADO_INACTIVO);
            u.setSesionesValidasDesde(ZonaHoraria.ahora().truncatedTo(ChronoUnit.SECONDS));
            int sesiones = refreshTokens.revocarTodos(u.getId());
            repository.save(u);
            auditoria.registrar("USUARIO_DESACTIVADO",
                    "Se desactivó la cuenta " + etiqueta(u) + " y se cerraron " + sesiones + " sesiones abiertas"
                            + (s.motivo() == null || s.motivo().isBlank() ? "" : ". Motivo: " + s.motivo().trim()),
                    actorId);
        } else {
            u.setEstado(Usuario.ESTADO_ACTIVO);
            repository.save(u);
            auditoria.registrar("USUARIO_ACTIVADO", "Se reactivó la cuenta " + etiqueta(u), actorId);
        }
        return UsuarioResponse.from(u);
    }

    private void exigirOtroAdministrador(Usuario u, String mensaje) {
        boolean esAdministradorActivo = u.getRol() != null
                && Roles.ADMINISTRADOR.equals(u.getRol().getNombre()) && u.estaActivo();
        if (esAdministradorActivo && repository.contarActivosConRol(Roles.ADMINISTRADOR) <= 1) {
            throw new ReglaNegocioException("estado", mensaje);
        }
    }

    /**
     * HU-34: perfil propio, con el acceso anterior y los intentos fallidos
     * desde entonces. El acceso más reciente es la sesión actual, por eso se
     * muestra el anterior.
     */
    @Transactional(readOnly = true)
    public PerfilResponse perfil(Long id) {
        Usuario u = buscar(id);
        List<Acceso> exitosos = accesos.findTop2ByUsuario_IdAndResultadoOrderByFechaDescIdDesc(id, Acceso.EXITOSO);
        Acceso anterior = exitosos.size() > 1 ? exitosos.get(1) : null;
        long fallidos = anterior == null ? 0
                : accesos.countByUsuario_IdAndResultadoAndFechaAfter(id, Acceso.FALLIDO, anterior.getFecha());
        return new PerfilResponse(UsuarioResponse.from(u), anterior == null ? null : anterior.getFecha(), fallidos);
    }

    /**
     * HU-34: edición del perfil propio. El usuario actualiza sus datos de
     * contacto; el rol, el estado, el área y el turno los asigna el
     * administrador, y pedirlos aquí es un error, no algo que se ignora en
     * silencio.
     */
    @Transactional
    public UsuarioResponse actualizarPerfil(Long id, UsuarioActualizacionRequest s) {
        if (s.rolId() != null || s.estado() != null) {
            throw new ReglaNegocioException("rolId", "El rol y el estado de la cuenta los asigna el administrador");
        }
        if (s.area() != null || s.turno() != null) {
            throw new ReglaNegocioException("area", "El área y el turno los asigna el administrador");
        }
        Usuario u = buscar(id);
        aplicarCambiosComunes(u, s);
        Usuario guardado = repository.save(u);
        auditoria.registrar("EDITAR PERFIL PROPIO", id);
        return UsuarioResponse.from(guardado);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void eliminar(Long id, Long actorId) {
        if (id.equals(actorId)) {
            throw new ReglaNegocioException("No puede eliminar su propia cuenta");
        }
        Usuario u = buscar(id);
        repository.delete(u);
        auditoria.registrar("ELIMINAR USUARIO: " + etiqueta(u), actorId);
    }

    private void aplicarCambiosComunes(Usuario u, UsuarioActualizacionRequest s) {
        if (s.nombres() != null) {
            u.setNombre(s.nombres().trim());
        }
        if (s.apellidos() != null) {
            u.setApellidos(s.apellidos().trim());
        }
        if (s.correo() != null) {
            String correo = s.correo().trim().toLowerCase();
            if (repository.existsByCorreoIgnoreCaseAndIdNot(correo, u.getId())) {
                throw new ConflictoException("correo", "El correo ya está registrado");
            }
            u.setCorreo(correo);
        }
        if (s.telefono() != null) {
            u.setTelefono(vacioANulo(s.telefono()));
        }
        if (s.area() != null) {
            u.setArea(s.area());
        }
        if (s.turno() != null) {
            u.setTurno(vacioANulo(s.turno()));
        }
        if (s.password() != null && !s.password().isBlank()) {
            if (!s.password().equals(s.confirmarPassword())) {
                throw new ReglaNegocioException("confirmarPassword", "La contraseña y su confirmación no coinciden");
            }
            u.setPassword(encoder.encode(s.password()));
        }
    }

    static void validarDocumento(String tipo, String numero) {
        String valor = numero == null ? "" : numero.trim();
        if ("DNI".equals(tipo) && !valor.matches("\\d{8}")) {
            throw new ReglaNegocioException("numeroDocumento", "El DNI debe tener 8 dígitos");
        }
        if (!"DNI".equals(tipo) && !valor.matches("[A-Za-z0-9]{6,20}")) {
            throw new ReglaNegocioException("numeroDocumento",
                    "El documento debe tener entre 6 y 20 caracteres alfanuméricos");
        }
    }

    private Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private Rol buscarRol(Long rolId) {
        return rolRepository.findById(rolId)
                .orElseThrow(() -> new ReglaNegocioException("rolId", "El rol seleccionado no existe"));
    }

    private static String etiqueta(Usuario u) {
        return u.getNombreUsuario() != null ? u.getNombreUsuario() : u.getCorreo();
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

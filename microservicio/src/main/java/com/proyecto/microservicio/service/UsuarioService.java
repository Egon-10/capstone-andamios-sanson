package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.UsuarioActualizacionRequest;
import com.proyecto.microservicio.dto.UsuarioRegistroRequest;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HU-43: registro de usuarios con validación de duplicados.
 * HU-07: edición parcial de datos de usuario.
 * HU-05: las operaciones de administración exigen el rol ADMINISTRADOR también
 * a nivel de método, además de la regla por endpoint.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final RolRepository rolRepository;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    public UsuarioService(UsuarioRepository repository, RolRepository rolRepository,
                          PasswordEncoder encoder, AuditoriaService auditoria) {
        this.repository = repository;
        this.rolRepository = rolRepository;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<UsuarioResponse> listar() {
        return repository.findAll().stream().map(UsuarioResponse::from).toList();
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
        u.setFechaCreacion(LocalDateTime.now());

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
            u.setRol(buscarRol(s.rolId()));
        }
        if (s.estado() != null) {
            if (id.equals(actorId) && Usuario.ESTADO_INACTIVO.equals(s.estado())) {
                throw new ReglaNegocioException("estado", "No puede desactivar su propia cuenta");
            }
            u.setEstado(s.estado());
        }
        Usuario guardado = repository.save(u);
        auditoria.registrar("EDITAR USUARIO: " + etiqueta(guardado), actorId);
        return UsuarioResponse.from(guardado);
    }

    /** Edición del perfil propio: no permite cambiar el rol ni el estado. */
    @Transactional
    public UsuarioResponse actualizarPerfil(Long id, UsuarioActualizacionRequest s) {
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

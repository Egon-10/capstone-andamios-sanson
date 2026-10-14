package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.repository.AuditoriaRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bitácora de auditoría.
 *
 * El registro lo hace siempre el servidor: el responsable se toma del token
 * de la sesión y nunca de lo que envía el cliente (CR-04). La consulta (HU-33)
 * admite filtros combinados y devuelve los resultados por páginas.
 */
@Service
public class AuditoriaService {

    /** Tamaños de las columnas accion y detalle de la tabla auditoria. */
    private static final int LARGO_ACCION = 255;
    private static final int LARGO_DETALLE = 500;

    public static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final Sort RECIENTES_PRIMERO = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));

    private final AuditoriaRepository repository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaService(AuditoriaRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Registra una acción a nombre del usuario indicado. El responsable siempre
     * lo determina el servidor a partir del token, nunca el cliente.
     */
    public Auditoria registrar(String accion, Long usuarioId) {
        return registrar(accion, null, usuarioId);
    }

    /**
     * Registra una acción con su explicación. El detalle se recorta al tamaño
     * de la columna para que un texto largo no haga fallar la operación que se
     * está auditando: perder parte del detalle es preferible a perder el
     * movimiento, y el registro de auditoría nunca debe ser el que rompa la
     * transacción del negocio.
     */
    public Auditoria registrar(String accion, String detalle, Long usuarioId) {
        Auditoria auditoria = new Auditoria();
        auditoria.setAccion(recortar(accion, LARGO_ACCION));
        auditoria.setDetalle(recortar(detalle, LARGO_DETALLE));
        auditoria.setFecha(ZonaHoraria.ahora());
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(auditoria::setUsuario);
        }
        return repository.save(auditoria);
    }

    /**
     * Registra una acción a nombre del usuario de la solicitud en curso. Lo
     * usan los servicios del catálogo, cuyos controladores no reciben el token.
     */
    public Auditoria registrarComoUsuarioActual(String accion, String detalle) {
        return registrar(accion, detalle, UsuarioAutenticado.actual().orElse(null));
    }

    /** HU-33: consulta paginada con filtros combinados, de lo más reciente a lo más antiguo. */
    @Transactional(readOnly = true)
    public Page<Auditoria> buscar(FiltroBitacora filtro, int pagina, int tamano) {
        return repository.buscar(filtro.patronTexto(), filtro.usuarioId(), filtro.inicio(), filtro.fin(),
                PageRequest.of(Math.max(pagina, 0), acotar(tamano), RECIENTES_PRIMERO));
    }

    /** Para el reporte: los registros más recientes que cumplen el filtro, hasta el tope indicado. */
    @Transactional(readOnly = true)
    public Page<Auditoria> buscarParaReporte(FiltroBitacora filtro, int tope) {
        return repository.buscar(filtro.patronTexto(), filtro.usuarioId(), filtro.inicio(), filtro.fin(),
                PageRequest.of(0, tope, RECIENTES_PRIMERO));
    }

    @Transactional(readOnly = true)
    public List<Auditoria> listarUltimos() {
        return repository.findTop10ByOrderByFechaDescIdDesc();
    }

    static int acotar(int tamano) {
        return Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA);
    }

    static String recortar(String valor, int largo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= largo ? valor : valor.substring(0, largo - 3) + "...";
    }
}

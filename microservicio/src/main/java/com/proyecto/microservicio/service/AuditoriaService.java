package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;

import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.repository.AuditoriaRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditoriaService {

    /** Tamaños de las columnas accion y detalle de la tabla auditoria. */
    private static final int LARGO_ACCION = 255;
    private static final int LARGO_DETALLE = 500;

    private final AuditoriaRepository repository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaService(AuditoriaRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<Auditoria> listar() {
        return repository.findAll();
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

    static String recortar(String valor, int largo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= largo ? valor : valor.substring(0, largo - 3) + "...";
    }

    public List<Auditoria> listarUltimos() {
        return repository.findAllByOrderByFechaDesc();
    }
}

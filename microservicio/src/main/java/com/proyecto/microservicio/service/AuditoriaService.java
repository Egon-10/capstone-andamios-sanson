package com.proyecto.microservicio.service;

import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.repository.AuditoriaRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditoriaService {

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
        Auditoria auditoria = new Auditoria();
        auditoria.setAccion(accion);
        auditoria.setFecha(LocalDateTime.now());
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(auditoria::setUsuario);
        }
        return repository.save(auditoria);
    }

    public List<Auditoria> listarUltimos() {
        return repository.findAllByOrderByFechaDesc();
    }
}

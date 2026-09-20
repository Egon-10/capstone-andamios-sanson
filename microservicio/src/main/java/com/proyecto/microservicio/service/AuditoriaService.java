package com.proyecto.microservicio.service;

import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.repository.AuditoriaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditoriaService {

    private final AuditoriaRepository repository;

    public AuditoriaService(AuditoriaRepository repository) {
        this.repository = repository;
    }

    public List<Auditoria> listar() {
        return repository.findAll();
    }

    public Auditoria guardar(
        Auditoria auditoria) {

    auditoria.setFecha(
        LocalDateTime.now()
    );

    return repository.save(auditoria);
}
public List<Auditoria> listarUltimos() {

    return repository
            .findAllByOrderByFechaDesc();
}
}
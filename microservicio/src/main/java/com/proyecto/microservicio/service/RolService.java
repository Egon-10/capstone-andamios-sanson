package com.proyecto.microservicio.service;

import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RolService {

    private final RolRepository repository;

    public RolService(RolRepository repository) {
        this.repository = repository;
    }

    public List<Rol> listar() {
        return repository.findAll();
    }

    public Rol obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException("Rol no encontrado"));
    }

    public Rol guardar(Rol rol) {
        return repository.save(rol);
    }

    public void eliminar(Long id) {
        repository.deleteById(id);
    }
}
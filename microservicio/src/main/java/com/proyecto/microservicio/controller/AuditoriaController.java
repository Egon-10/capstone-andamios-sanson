package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.service.AuditoriaService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@CrossOrigin(origins = "*")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Auditoria> listar() {
        return service.listar();
    }

    @PostMapping
    public Auditoria guardar(
            @RequestBody Auditoria auditoria) {

        return service.guardar(auditoria);
    }
    @GetMapping("/ultimos")
public List<Auditoria> listarUltimos() {

    return service.listarUltimos();
}
}
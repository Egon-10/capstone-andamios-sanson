package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.service.AuditoriaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Auditoria> listar() {
        return service.listar();
    }

    /** El usuario responsable se toma del token, no del cuerpo enviado por el cliente. */
    @PostMapping
    public Auditoria guardar(@RequestBody Auditoria auditoria, @AuthenticationPrincipal Jwt jwt) {
        return service.registrar(auditoria.getAccion(), Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/ultimos")
    public List<Auditoria> listarUltimos() {
        return service.listarUltimos();
    }
}

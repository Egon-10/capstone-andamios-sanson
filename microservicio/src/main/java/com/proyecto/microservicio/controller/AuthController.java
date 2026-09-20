package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.model.LoginDTO;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.service.UsuarioService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UsuarioService service;

    public AuthController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public Usuario login(@RequestBody LoginDTO login) {
        return service.login(login);
    }
}
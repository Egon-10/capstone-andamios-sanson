package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.LoginRequest;
import com.proyecto.microservicio.dto.LoginResponse;
import com.proyecto.microservicio.dto.RefreshRequest;
import com.proyecto.microservicio.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest solicitud) {
        return service.login(solicitud);
    }

    @PostMapping("/refresh")
    public LoginResponse renovar(@Valid @RequestBody RefreshRequest solicitud) {
        return service.renovar(solicitud.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest solicitud,
                                       @AuthenticationPrincipal Jwt jwt) {
        service.logout(solicitud == null ? null : solicitud.refreshToken(),
                jwt.getId(), jwt.getExpiresAt(), Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}

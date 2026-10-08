package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.CambioPasswordRequest;
import com.proyecto.microservicio.dto.LoginRequest;
import com.proyecto.microservicio.dto.LoginResponse;
import com.proyecto.microservicio.dto.RefreshRequest;
import com.proyecto.microservicio.security.OrigenSolicitud;
import com.proyecto.microservicio.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
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
    public LoginResponse login(@Valid @RequestBody LoginRequest solicitud, HttpServletRequest http) {
        return service.login(solicitud, OrigenSolicitud.de(http));
    }

    @PostMapping("/refresh")
    public LoginResponse renovar(@Valid @RequestBody RefreshRequest solicitud) {
        return service.renovar(solicitud.refreshToken());
    }

    /** HU-37: cambio de la contraseña propia; devuelve la sesión nueva. */
    @PostMapping("/cambio-password")
    public LoginResponse cambiarPassword(@Valid @RequestBody CambioPasswordRequest solicitud,
                                         @AuthenticationPrincipal Jwt jwt) {
        return service.cambiarPassword(Long.valueOf(jwt.getSubject()), solicitud);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest solicitud,
                                       @AuthenticationPrincipal Jwt jwt) {
        service.logout(solicitud == null ? null : solicitud.refreshToken(),
                jwt.getId(), jwt.getExpiresAt(), Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}

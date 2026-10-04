package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.UsuarioActualizacionRequest;
import com.proyecto.microservicio.dto.UsuarioRegistroRequest;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/me")
    public UsuarioResponse miPerfil(@AuthenticationPrincipal Jwt jwt) {
        return service.obtener(idDe(jwt));
    }

    @PatchMapping("/me")
    public UsuarioResponse actualizarMiPerfil(@Valid @RequestBody UsuarioActualizacionRequest solicitud,
                                              @AuthenticationPrincipal Jwt jwt) {
        return service.actualizarPerfil(idDe(jwt), solicitud);
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody UsuarioRegistroRequest solicitud,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(solicitud, idDe(jwt)));
    }

    @PatchMapping("/{id}")
    public UsuarioResponse actualizarParcial(@PathVariable Long id,
                                             @Valid @RequestBody UsuarioActualizacionRequest solicitud,
                                             @AuthenticationPrincipal Jwt jwt) {
        return service.actualizarParcial(id, solicitud, idDe(jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.eliminar(id, idDe(jwt));
        return ResponseEntity.noContent().build();
    }

    private static Long idDe(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

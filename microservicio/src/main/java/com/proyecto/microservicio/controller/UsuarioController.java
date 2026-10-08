package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.CambioEstadoRequest;
import com.proyecto.microservicio.dto.PaginaResponse;
import com.proyecto.microservicio.dto.PerfilResponse;
import com.proyecto.microservicio.dto.UsuarioActualizacionRequest;
import com.proyecto.microservicio.dto.UsuarioOpcion;
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

    /** HU-30: listado con búsqueda, filtros y paginación en el servidor. */
    @GetMapping
    public PaginaResponse<UsuarioResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long rolId,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano,
            @RequestParam(defaultValue = "nombre") String orden,
            @RequestParam(defaultValue = "false") boolean descendente) {
        return PaginaResponse.de(service.buscar(texto, rolId, estado, pagina, tamano, orden, descendente),
                UsuarioResponse::from);
    }

    /** Lista corta para filtros; la pueden consultar quienes ven la bitácora. */
    @GetMapping("/opciones")
    public List<UsuarioOpcion> opciones() {
        return service.opciones();
    }

    /** HU-34: perfil propio con el último acceso. */
    @GetMapping("/me")
    public PerfilResponse miPerfil(@AuthenticationPrincipal Jwt jwt) {
        return service.perfil(idDe(jwt));
    }

    /** HU-31: activa o desactiva una cuenta. */
    @PatchMapping("/{id}/estado")
    public UsuarioResponse cambiarEstado(@PathVariable Long id,
                                         @Valid @RequestBody CambioEstadoRequest solicitud,
                                         @AuthenticationPrincipal Jwt jwt) {
        return service.cambiarEstado(id, solicitud, idDe(jwt));
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

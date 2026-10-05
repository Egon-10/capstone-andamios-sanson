package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.AjusteRequest;
import com.proyecto.microservicio.dto.AjusteResponse;
import com.proyecto.microservicio.dto.RechazoRequest;
import com.proyecto.microservicio.security.UsuarioAutenticado;
import com.proyecto.microservicio.service.AjusteInventarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HU-16: ajustes de inventario por conteo físico.
 *
 * Quien registra el conteo y quien lo aprueba se identifican por su token
 * (CR-04). Las rutas de aprobación y rechazo están restringidas al
 * administrador y al gerente en SecurityConfig, de modo que el encargado que
 * cuenta no puede autorizar su propia diferencia.
 */
@RestController
@RequestMapping("/api/ajustes")
public class AjusteInventarioController {

    private final AjusteInventarioService service;

    public AjusteInventarioController(AjusteInventarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<AjusteResponse> listar(@RequestParam(required = false) String estado,
                                       @RequestParam(required = false) Long productoId) {
        if (productoId != null) {
            return mapear(service.listarPorProducto(productoId));
        }
        if ("PENDIENTE".equalsIgnoreCase(estado)) {
            return mapear(service.listarPendientes());
        }
        return mapear(service.listar());
    }

    @GetMapping("/{id}")
    public AjusteResponse obtener(@PathVariable Long id) {
        return AjusteResponse.de(service.obtener(id));
    }

    /** Registra el conteo. Deja el ajuste pendiente sin tocar el inventario. */
    @PostMapping
    public ResponseEntity<AjusteResponse> registrar(@Valid @RequestBody AjusteRequest solicitud,
                                                    @AuthenticationPrincipal Jwt jwt) {
        AjusteResponse creado = AjusteResponse.de(
                service.registrar(solicitud, UsuarioAutenticado.id(jwt)));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /** Aprueba el conteo y corrige el inventario con un movimiento de ajuste. */
    @PostMapping("/{id}/aprobacion")
    public AjusteResponse aprobar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return AjusteResponse.de(service.aprobar(id, UsuarioAutenticado.id(jwt)));
    }

    @PostMapping("/{id}/rechazo")
    public AjusteResponse rechazar(@PathVariable Long id,
                                   @Valid @RequestBody RechazoRequest solicitud,
                                   @AuthenticationPrincipal Jwt jwt) {
        return AjusteResponse.de(service.rechazar(id, solicitud, UsuarioAutenticado.id(jwt)));
    }

    private static List<AjusteResponse> mapear(
            List<com.proyecto.microservicio.model.AjusteInventario> ajustes) {
        return ajustes.stream().map(AjusteResponse::de).toList();
    }
}

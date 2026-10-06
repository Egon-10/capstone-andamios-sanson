package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.MotivoResponse;
import com.proyecto.microservicio.model.TiposMovimiento;
import com.proyecto.microservicio.service.MotivoMovimientoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * HU-14: catálogo de motivos tipificados.
 *
 * El cliente lo consulta para llenar el desplegable del formulario de
 * movimiento. La validación real ocurre en el servidor: que el motivo exista,
 * esté activo y corresponda al tipo de movimiento no depende de que el cliente
 * haya pedido la lista correcta.
 */
@RestController
@RequestMapping("/api/motivos-movimiento")
public class MotivoMovimientoController {

    private final MotivoMovimientoService service;

    public MotivoMovimientoController(MotivoMovimientoService service) {
        this.service = service;
    }

    /**
     * Motivos disponibles. Con el parámetro tipo se limita a los que aplican a
     * una entrada o a una salida, que es lo que necesita el formulario.
     */
    @GetMapping
    public List<MotivoResponse> listar(@RequestParam(required = false) String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return service.listarSeleccionables().stream().map(MotivoResponse::de).toList();
        }
        String normalizado = tipo.trim().toUpperCase(Locale.ROOT);
        if (!TiposMovimiento.esValido(normalizado)) {
            throw new com.proyecto.microservicio.exception.ReglaNegocioException(
                    "tipo", "El tipo de movimiento debe ser ENTRADA o SALIDA");
        }
        return service.listarPorTipo(normalizado).stream().map(MotivoResponse::de).toList();
    }
}

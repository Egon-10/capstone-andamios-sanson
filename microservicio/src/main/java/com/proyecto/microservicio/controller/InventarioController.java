package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.KardexResponse;
import com.proyecto.microservicio.dto.ValorizacionResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.service.KardexService;
import com.proyecto.microservicio.service.ValorizacionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Consultas de inventario: valorización (HU-17), kardex (HU-18) y productos
 * por reponer (HU-20).
 *
 * Son todas de lectura. El control de acceso lo aplica SecurityConfig: la
 * valorización es información económica del negocio y queda para el
 * administrador y el gerente, mientras que el kardex y la lista de reposición
 * los necesita también el encargado para operar el almacén.
 */
@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final ValorizacionService valorizacion;
    private final KardexService kardex;

    public InventarioController(ValorizacionService valorizacion, KardexService kardex) {
        this.valorizacion = valorizacion;
        this.kardex = kardex;
    }

    /** HU-17: valor del inventario al costo promedio ponderado. */
    @GetMapping("/valorizacion")
    public ValorizacionResponse valorizacion() {
        return valorizacion.calcular();
    }

    /** HU-20: productos que alcanzaron su umbral de reposición. */
    @GetMapping("/por-reponer")
    public List<ValorizacionResponse.PorProducto> porReponer() {
        return valorizacion.porReponer();
    }

    /** HU-18: kardex de un producto, opcionalmente acotado por fechas. */
    @GetMapping("/kardex/{productoId}")
    public KardexResponse kardex(@PathVariable Long productoId,
                                 @RequestParam(required = false) String desde,
                                 @RequestParam(required = false) String hasta) {
        return kardex.obtener(productoId, inicioDelDia(desde), finDelDia(hasta));
    }

    private static LocalDateTime inicioDelDia(String fecha) {
        LocalDate dia = comoFecha(fecha);
        return dia == null ? null : dia.atStartOfDay();
    }

    private static LocalDateTime finDelDia(String fecha) {
        LocalDate dia = comoFecha(fecha);
        return dia == null ? null : dia.atTime(LocalTime.MAX);
    }

    private static LocalDate comoFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(fecha.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException("fecha",
                    "La fecha " + fecha + " no tiene el formato AAAA-MM-DD");
        }
    }
}

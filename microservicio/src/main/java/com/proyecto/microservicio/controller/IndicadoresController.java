package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.IndicadoresResponse;
import com.proyecto.microservicio.dto.StockCriticoResponse;
import com.proyecto.microservicio.dto.TendenciaResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.service.IndicadoresService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * HU-23 a HU-25: panel de indicadores.
 *
 * El resumen incluye el valor del inventario, que es informacion economica, y
 * queda para el administrador y el gerente. La tendencia y el stock critico
 * los necesita tambien el encargado para planificar el almacen. Las reglas
 * estan en SecurityConfig.
 */
@RestController
@RequestMapping("/api/indicadores")
public class IndicadoresController {

    private final IndicadoresService service;

    public IndicadoresController(IndicadoresService service) {
        this.service = service;
    }

    @GetMapping("/resumen")
    public IndicadoresResponse resumen() {
        return service.indicadores();
    }

    @GetMapping("/tendencia")
    public TendenciaResponse tendencia(@RequestParam(required = false) String desde,
                                       @RequestParam(required = false) String hasta) {
        return service.tendencia(comoFecha(desde), comoFecha(hasta));
    }

    @GetMapping("/stock-critico")
    public List<StockCriticoResponse> stockCritico() {
        return service.stockCritico();
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

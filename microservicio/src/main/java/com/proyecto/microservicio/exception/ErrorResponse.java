package com.proyecto.microservicio.exception;

import com.proyecto.microservicio.config.ZonaHoraria;

import java.time.LocalDateTime;
import java.util.Map;

/** Cuerpo de error uniforme de la API: nunca incluye trazas técnicas. */
public record ErrorResponse(
        LocalDateTime fecha,
        int estado,
        String error,
        String mensaje,
        Map<String, String> errores) {

    public static ErrorResponse of(int estado, String error, String mensaje) {
        return new ErrorResponse(ZonaHoraria.ahora(), estado, error, mensaje, Map.of());
    }

    public static ErrorResponse of(int estado, String error, String mensaje, Map<String, String> errores) {
        return new ErrorResponse(ZonaHoraria.ahora(), estado, error, mensaje, errores);
    }
}

package com.proyecto.microservicio.exception;

import com.proyecto.microservicio.config.ZonaHoraria;

import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.MDC;
import com.proyecto.microservicio.config.SolicitudFilter;

/**
 * Cuerpo de error uniforme de la API: nunca incluye trazas técnicas.
 *
 * HU-41: lleva el identificador de la solicitud, el mismo que aparece en el
 * registro del servidor. Si el usuario lo informa, se encuentra el error en
 * el registro sin adivinar a qué hora ocurrió.
 */
public record ErrorResponse(
        LocalDateTime fecha,
        int estado,
        String error,
        String mensaje,
        Map<String, String> errores,
        String solicitud) {

    public static ErrorResponse of(int estado, String error, String mensaje) {
        return of(estado, error, mensaje, Map.of());
    }

    public static ErrorResponse of(int estado, String error, String mensaje, Map<String, String> errores) {
        return new ErrorResponse(ZonaHoraria.ahora(), estado, error, mensaje, errores,
                MDC.get(SolicitudFilter.CLAVE_SOLICITUD));
    }
}

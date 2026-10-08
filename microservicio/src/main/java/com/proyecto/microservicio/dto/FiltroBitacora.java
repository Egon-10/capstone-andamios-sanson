package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.exception.ReglaNegocioException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * HU-32 y HU-33: filtros comunes de las bitácoras de auditoría y de accesos.
 *
 * Las fechas son días completos: "hasta" incluye todo el día indicado, por
 * eso se convierte en el primer instante del día siguiente y se compara con
 * menor estricto.
 */
public record FiltroBitacora(String texto, Long usuarioId, String resultado,
                             LocalDate desde, LocalDate hasta) {

    public FiltroBitacora {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("desde", "La fecha inicial no puede ser posterior a la final");
        }
    }

    public LocalDateTime inicio() {
        return desde == null ? null : desde.atStartOfDay();
    }

    public LocalDateTime fin() {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay();
    }

    /**
     * Patrón LIKE para el texto: en minúsculas, con los comodines que haya
     * escrito el usuario escapados con ! para que se busquen literalmente (se usa ! y no la
     * barra invertida porque MySQL interpreta la barra dentro de los literales), y rodeado
     * de % para buscar en cualquier parte. Nulo si no hay texto.
     */
    public String patronTexto() {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String escapado = texto.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escapado + "%";
    }
}

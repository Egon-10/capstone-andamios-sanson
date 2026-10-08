package com.proyecto.microservicio.security;

import com.proyecto.microservicio.config.SolicitudFilter;
import com.proyecto.microservicio.config.ZonaHoraria;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escribe un error con la misma forma que ErrorResponse desde un filtro, que
 * corre antes de los controladores y no pasa por el manejador global.
 */
final class RespuestaJson {

    private RespuestaJson() {
    }

    static void error(HttpServletResponse respuesta, int estado, String error, String mensaje) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.setContentType("application/json");
        String solicitud = MDC.get(SolicitudFilter.CLAVE_SOLICITUD);
        respuesta.getWriter().write("{\"fecha\":\"" + ZonaHoraria.ahora() + "\""
                + ",\"estado\":" + estado
                + ",\"error\":\"" + escapar(error) + "\""
                + ",\"mensaje\":\"" + escapar(mensaje) + "\""
                + ",\"errores\":{}"
                + ",\"solicitud\":" + (solicitud == null ? "null" : "\"" + escapar(solicitud) + "\"")
                + "}");
    }

    static String escapar(String valor) {
        StringBuilder s = new StringBuilder(valor.length() + 8);
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> s.append("\\\"");
                case '\\' -> s.append("\\\\");
                case '\n' -> s.append("\\n");
                case '\r' -> s.append("\\r");
                case '\t' -> s.append("\\t");
                default -> {
                    if (c < 0x20) {
                        s.append(String.format("\\u%04x", (int) c));
                    } else {
                        s.append(c);
                    }
                }
            }
        }
        return s.toString();
    }
}

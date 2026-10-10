package com.proyecto.microservicio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HU-41: trazabilidad de cada solicitud.
 *
 * Asigna a cada solicitud un identificador (o respeta el que envía el proxy
 * en X-Request-Id, si tiene un formato seguro), lo pone en el contexto de los
 * registros y lo devuelve en la respuesta. Al terminar deja una línea con el
 * método, la ruta, el estado y la duración; las solicitudes lentas se
 * registran como advertencia. Con eso se puede seguir una operación de punta a
 * punta y medir el desempeño sin herramientas adicionales.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SolicitudFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Request-Id";
    public static final String CLAVE_SOLICITUD = "solicitud";
    public static final String CLAVE_USUARIO = "usuario";

    /** Umbral a partir del cual una solicitud se considera lenta. */
    static final long MILISEGUNDOS_LENTA = 1000;

    private static final Logger log = LoggerFactory.getLogger("acceso");
    private static final Pattern ID_SEGURO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String id = identificador(solicitud.getHeader(CABECERA));
        long inicio = System.nanoTime();
        MDC.put(CLAVE_SOLICITUD, id);
        respuesta.setHeader(CABECERA, id);
        try {
            cadena.doFilter(solicitud, respuesta);
        } finally {
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            registrar(solicitud.getMethod(), solicitud.getRequestURI(), respuesta.getStatus(), ms);
            MDC.remove(CLAVE_SOLICITUD);
            MDC.remove(CLAVE_USUARIO);
        }
    }

    /** Respeta el identificador recibido solo si es seguro de escribir en los registros. */
    public static String identificador(String recibido) {
        if (recibido != null && ID_SEGURO.matcher(recibido).matches()) {
            return recibido;
        }
        return UUID.randomUUID().toString();
    }

    private static void registrar(String metodo, String ruta, int estado, long ms) {
        if (ruta.startsWith("/actuator/health")) {
            return;
        }
        if (ms >= MILISEGUNDOS_LENTA) {
            log.warn("{} {} -> {} en {} ms (lenta)", metodo, ruta, estado, ms);
        } else if (estado >= 500) {
            log.error("{} {} -> {} en {} ms", metodo, ruta, estado, ms);
        } else {
            log.info("{} {} -> {} en {} ms", metodo, ruta, estado, ms);
        }
    }
}

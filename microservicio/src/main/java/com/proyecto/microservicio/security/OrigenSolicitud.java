package com.proyecto.microservicio.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HU-32: desde dónde llegó una solicitud de inicio de sesión.
 *
 * La IP es la que entrega el contenedor. Detrás del proxy inverso, Spring la
 * toma de X-Forwarded-For solo si server.forward-headers-strategy está
 * activado, y eso se hace únicamente en el despliegue, donde el backend no es
 * accesible directamente: así un cliente no puede falsear su IP enviando la
 * cabecera él mismo.
 */
public record OrigenSolicitud(String ip, String agente) {

    private static final int LARGO_IP = 45;
    private static final int LARGO_AGENTE = 255;

    public static final OrigenSolicitud DESCONOCIDO = new OrigenSolicitud(null, null);

    public static OrigenSolicitud de(HttpServletRequest solicitud) {
        if (solicitud == null) {
            return DESCONOCIDO;
        }
        return new OrigenSolicitud(
                recortar(solicitud.getRemoteAddr(), LARGO_IP),
                recortar(solicitud.getHeader("User-Agent"), LARGO_AGENTE));
    }

    static String recortar(String valor, int largo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() <= largo ? limpio : limpio.substring(0, largo);
    }
}

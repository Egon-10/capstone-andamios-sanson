package com.proyecto.microservicio.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CP-33: el origen del intento de acceso se registra sin romper con valores extremos. */
class OrigenSolicitudTest {

    @Test
    @DisplayName("Toma la IP y el navegador de la solicitud")
    void tomaIpYNavegador() {
        HttpServletRequest solicitud = mock(HttpServletRequest.class);
        when(solicitud.getRemoteAddr()).thenReturn("192.168.1.20");
        when(solicitud.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        OrigenSolicitud o = OrigenSolicitud.de(solicitud);

        assertEquals("192.168.1.20", o.ip());
        assertEquals("Mozilla/5.0", o.agente());
    }

    @Test
    @DisplayName("Recorta valores largos y tolera la falta de solicitud")
    void valoresExtremos() {
        assertEquals(OrigenSolicitud.DESCONOCIDO, OrigenSolicitud.de(null));
        assertEquals(45, OrigenSolicitud.recortar("1".repeat(80), 45).length());
        assertNull(OrigenSolicitud.recortar("  ", 45));
    }
}

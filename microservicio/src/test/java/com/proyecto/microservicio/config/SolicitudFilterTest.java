package com.proyecto.microservicio.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** HU-41: lo que el cliente envía no puede alterar el formato del registro. */
class SolicitudFilterTest {

    @Test
    @DisplayName("Quita saltos de línea y tabulaciones antes de registrar")
    void limpiaValoresDelCliente() {
        assertEquals("/api/x__INFO falso", SolicitudFilter.limpiar("/api/x\r\nINFO falso"));
        assertEquals("GET", SolicitudFilter.limpiar("GET"));
        assertEquals("", SolicitudFilter.limpiar(null));
    }
}

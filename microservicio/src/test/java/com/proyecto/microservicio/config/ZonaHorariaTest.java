package com.proyecto.microservicio.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Las fechas se toman en la hora de Lima, no en la del servidor. */
class ZonaHorariaTest {

    @Test
    @DisplayName("La zona del negocio es America/Lima")
    void zonaDelNegocio() {
        assertEquals(ZoneId.of("America/Lima"), ZonaHoraria.NEGOCIO);
    }

    @Test
    @DisplayName("ahora() devuelve la hora de Lima aunque el servidor este en otra zona")
    void ahoraEnLima() {
        LocalDateTime esperado = LocalDateTime.now(ZoneId.of("America/Lima"));
        long diferencia = Math.abs(ChronoUnit.SECONDS.between(esperado, ZonaHoraria.ahora()));
        assertTrue(diferencia < 5, "diferencia de " + diferencia + " segundos");
    }
}

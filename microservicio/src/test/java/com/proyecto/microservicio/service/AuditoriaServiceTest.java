package com.proyecto.microservicio.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El registro de auditoría no debe ser nunca lo que haga fallar la operación
 * que está auditando, de modo que un detalle más largo que la columna se
 * recorta en lugar de provocar un error de base de datos.
 */
class AuditoriaServiceTest {

    @Test
    @DisplayName("Recorta el detalle que excede el tamano de la columna")
    void recortaElDetalleLargo() {
        String largo = "x".repeat(600);

        String recortado = AuditoriaService.recortar(largo, 500);

        assertEquals(500, recortado.length());
        assertTrue(recortado.endsWith("..."));
    }

    @Test
    @DisplayName("Deja intacto el detalle que cabe")
    void dejaIntactoElCorto() {
        assertEquals("Movimiento 12 anulado",
                AuditoriaService.recortar("Movimiento 12 anulado", 500));
        assertNull(AuditoriaService.recortar(null, 500));
    }

    @Test
    @DisplayName("Un texto exactamente del largo de la columna no se recorta")
    void largoExacto() {
        String exacto = "y".repeat(500);

        assertEquals(exacto, AuditoriaService.recortar(exacto, 500));
    }
}

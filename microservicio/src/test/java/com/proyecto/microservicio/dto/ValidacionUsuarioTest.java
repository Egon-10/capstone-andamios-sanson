package com.proyecto.microservicio.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEF-19: el formulario envía el turno vacío cuando se elige "Sin turno
 * asignado", y la validación lo rechazaba: no se podía crear una cuenta sin
 * turno. La prueba de sistema PA-08 lo detectó.
 */
class ValidacionUsuarioTest {

    @Test
    @DisplayName("El turno es opcional: vacío es válido, un valor fuera del catálogo no")
    void turnoOpcional() {
        assertTrue("".matches(ValidacionUsuario.TURNOS));
        assertTrue("TARDE".matches(ValidacionUsuario.TURNOS));
        assertFalse("DOMINGO".matches(ValidacionUsuario.TURNOS));
    }

    @Test
    @DisplayName("El teléfono es opcional y solo admite dígitos")
    void telefonoOpcional() {
        assertTrue("".matches(ValidacionUsuario.TELEFONO));
        assertFalse("98-765".matches(ValidacionUsuario.TELEFONO));
    }
}

package com.proyecto.microservicio.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/** CP-01: los errores llegan al cliente con un código HTTP y un mensaje sin detalles técnicos. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler manejador = new GlobalExceptionHandler();

    @Test
    @DisplayName("CP-01: un error no controlado responde 500 sin exponer el detalle interno")
    void errorNoControlado() {
        ResponseEntity<ErrorResponse> r = manejador.generico(
                new IllegalStateException("SQL: SELECT * FROM usuarios WHERE password = ..."));

        assertEquals(500, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertFalse(r.getBody().mensaje().contains("SQL"));
        assertFalse(r.getBody().mensaje().contains("usuarios"));
    }

    @Test
    @DisplayName("Un conflicto responde 409 e indica el campo afectado")
    void conflicto() {
        ResponseEntity<ErrorResponse> r = manejador.conflicto(
                new ConflictoException("correo", "El correo ya está registrado"));

        assertEquals(409, r.getStatusCode().value());
        assertEquals("El correo ya está registrado", r.getBody().errores().get("correo"));
    }

    @Test
    @DisplayName("Un recurso inexistente responde 404")
    void noEncontrado() {
        assertEquals(404, manejador.noEncontrado(new RecursoNoEncontradoException("Producto no encontrado"))
                .getStatusCode().value());
    }

    @Test
    @DisplayName("Una regla de negocio incumplida responde 422")
    void reglaNegocio() {
        assertEquals(422, manejador.reglaNegocio(new ReglaNegocioException("Stock insuficiente"))
                .getStatusCode().value());
    }

    @Test
    @DisplayName("HU-13: el bloqueo optimista responde 409 y pide reintentar")
    void bloqueoOptimista() {
        ResponseEntity<ErrorResponse> r = manejador.bloqueoOptimista(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        com.proyecto.microservicio.model.Producto.class, 1L));

        assertEquals(409, r.getStatusCode().value());
        assertTrue(r.getBody().mensaje().contains("Vuelva a intentarlo"));
    }
}

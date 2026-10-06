package com.proyecto.microservicio.exception;

/**
 * Credenciales o token de renovación inválidos (HTTP 401).
 * El mensaje es siempre genérico para no revelar qué dato falló.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales inválidas");
    }

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}

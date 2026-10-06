package com.proyecto.microservicio.exception;

/**
 * El dato enviado entra en conflicto con uno existente (HTTP 409),
 * por ejemplo un correo o un SKU ya registrado.
 */
public class ConflictoException extends RuntimeException {

    private final String campo;

    public ConflictoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}

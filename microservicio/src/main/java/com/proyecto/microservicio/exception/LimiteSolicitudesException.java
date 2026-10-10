package com.proyecto.microservicio.exception;

/** HU-38: demasiadas solicitudes seguidas desde el mismo origen (HTTP 429). */
public class LimiteSolicitudesException extends RuntimeException {

    private final long segundosDeEspera;

    public LimiteSolicitudesException(long segundosDeEspera) {
        super("Demasiados intentos seguidos. Espere " + segundosDeEspera + " segundos e intente de nuevo.");
        this.segundosDeEspera = segundosDeEspera;
    }

    public long getSegundosDeEspera() {
        return segundosDeEspera;
    }
}

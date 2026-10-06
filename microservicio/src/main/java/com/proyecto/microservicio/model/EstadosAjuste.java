package com.proyecto.microservicio.model;

/** Estados de un ajuste de inventario por conteo físico (HU-16). */
public final class EstadosAjuste {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";

    private EstadosAjuste() {
    }
}

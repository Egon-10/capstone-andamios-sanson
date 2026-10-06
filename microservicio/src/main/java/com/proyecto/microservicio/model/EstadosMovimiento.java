package com.proyecto.microservicio.model;

/**
 * Estados de un movimiento (HU-15).
 *
 * Un movimiento no se borra nunca: se anula. La anulación marca el asiento
 * original como ANULADO y crea un segundo asiento de tipo contrario en estado
 * COMPENSACION. Las dos filas permanecen en el kardex y su efecto sobre el
 * stock se cancela, de modo que el inventario vuelve al valor que tenía antes
 * del error sin que se pierda la traza de lo ocurrido.
 *
 * Por eso ANULADO es una marca de presentación y no una exclusión del saldo:
 * el asiento anulado sigue contando, y lo que lo revierte es el asiento
 * compensatorio. Descontar el original y además sumar el compensatorio
 * corregiría el stock dos veces.
 */
public final class EstadosMovimiento {

    /** Asiento vigente, registrado por el flujo normal de entrada o salida. */
    public static final String REGISTRADO = "REGISTRADO";

    /** Asiento que fue revertido por una anulación. Sigue contando para el saldo. */
    public static final String ANULADO = "ANULADO";

    /** Asiento generado por una anulación para revertir a otro. */
    public static final String COMPENSACION = "COMPENSACION";

    private EstadosMovimiento() {
    }

    /** Un asiento solo se puede anular una vez, y un compensatorio no se anula. */
    public static boolean sePuedeAnular(String estado) {
        return REGISTRADO.equals(estado);
    }
}

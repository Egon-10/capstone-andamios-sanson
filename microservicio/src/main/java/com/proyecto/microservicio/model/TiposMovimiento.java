package com.proyecto.microservicio.model;

/** Tipos de movimiento registrados en la columna movimientos.tipo. */
public final class TiposMovimiento {

    public static final String ENTRADA = "ENTRADA";
    public static final String SALIDA = "SALIDA";

    private TiposMovimiento() {
    }

    public static boolean esValido(String tipo) {
        return ENTRADA.equals(tipo) || SALIDA.equals(tipo);
    }

    /** El tipo contrario, que es el que usa el asiento compensatorio de la HU-15. */
    public static String contrario(String tipo) {
        return ENTRADA.equals(tipo) ? SALIDA : ENTRADA;
    }

    /** Signo con el que el tipo afecta al stock: +1 en la entrada, -1 en la salida. */
    public static int signo(String tipo) {
        return ENTRADA.equals(tipo) ? 1 : -1;
    }
}

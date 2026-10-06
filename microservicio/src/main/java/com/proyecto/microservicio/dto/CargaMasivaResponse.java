package com.proyecto.microservicio.dto;

import java.util.List;

/**
 * HU-19: resultado de una carga masiva de productos.
 *
 * El informe nombra la fila y el motivo de cada rechazo. Un mensaje del tipo
 * "el archivo tiene errores" obligaría a revisar un archivo de cientos de
 * filas a ojo, de modo que la utilidad de la carga depende de este detalle.
 */
public record CargaMasivaResponse(
        String modo,
        boolean simulacion,
        int filasLeidas,
        int aceptadas,
        int rechazadas,
        boolean seGuardo,
        List<Fila> errores,
        List<String> skusCargados) {

    /** Fila rechazada, con el número que tiene en el archivo. */
    public record Fila(int fila, String sku, String motivo) {
    }
}

package com.proyecto.microservicio.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * HU-17: valorización del inventario por promedio ponderado.
 *
 * El valor de cada producto es su stock por su costo promedio, que la entrada
 * recalcula en cada ingreso. No se usa el precio de venta: valorizar al precio
 * sobreestimaría el inventario en el margen comercial.
 */
public record ValorizacionResponse(
        BigDecimal valorTotal,
        Integer unidadesTotales,
        Integer productosContados,
        List<PorCategoria> porCategoria,
        List<PorProducto> detalle) {

    public record PorCategoria(
            String categoria,
            Integer productos,
            Integer unidades,
            BigDecimal valor,
            BigDecimal participacion) {
    }

    public record PorProducto(
            Long productoId,
            String sku,
            String producto,
            String categoria,
            Integer stock,
            BigDecimal costoPromedio,
            BigDecimal valor,
            boolean necesitaReposicion) {
    }
}

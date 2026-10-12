package com.proyecto.microservicio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-17: valorización del inventario por promedio ponderado.
 * HU-28: la misma valorización con corte a una fecha pasada.
 *
 * El valor de cada producto es su stock por su costo promedio, que la entrada
 * recalcula en cada ingreso. No se usa el precio de venta: valorizar al precio
 * sobreestimaría el inventario en el margen comercial.
 *
 * @param fechaCorte                 día de corte; nulo cuando es la valorización actual
 * @param productosConCostoEstimado  productos cuyo costo al corte no se pudo
 *                                   reconstruir y se tomó del costo vigente
 */
public record ValorizacionResponse(
        BigDecimal valorTotal,
        Integer unidadesTotales,
        Integer productosContados,
        List<PorCategoria> porCategoria,
        List<PorProducto> detalle,
        LocalDate fechaCorte,
        Integer productosConCostoEstimado) {

    public record PorCategoria(
            String categoria,
            Integer productos,
            Integer unidades,
            BigDecimal valor,
            BigDecimal participacion) {
    }

    /**
     * @param costoEstimado verdadero si el costo al corte no se conoce y se usó el vigente
     */
    public record PorProducto(
            Long productoId,
            String sku,
            String producto,
            String categoria,
            Integer stock,
            BigDecimal costoPromedio,
            BigDecimal valor,
            boolean necesitaReposicion,
            boolean costoEstimado) {
    }
}

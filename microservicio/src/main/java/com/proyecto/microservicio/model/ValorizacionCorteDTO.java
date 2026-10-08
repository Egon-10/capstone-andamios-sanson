package com.proyecto.microservicio.model;

import java.math.BigDecimal;

/**
 * HU-28: producto con los datos necesarios para valorizarlo a una fecha de
 * corte. El stock ya viene reconstruido; el costo se decide en el servicio
 * porque depende de si el movimiento anterior al corte guardó su costo.
 */
public interface ValorizacionCorteDTO {

    Long getProductoId();

    String getSku();

    String getProducto();

    String getCategoria();

    /** Stock que había al cierre del día de corte. */
    Integer getStock();

    Integer getStockMinimo();

    Integer getPuntoReposicion();

    /** Costo promedio que dejó el último movimiento anterior al corte, si lo guardó. */
    BigDecimal getCostoHistorico();

    /** Costo promedio vigente hoy. */
    BigDecimal getCostoVigente();

    /** Movimientos registrados después del corte. */
    Long getMovimientosPosteriores();
}

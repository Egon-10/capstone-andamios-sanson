package com.proyecto.microservicio.model;

import java.math.BigDecimal;

/** HU-26 y HU-27: producto tal como aparece en el reporte del catálogo. */
public interface ReporteProductoDTO {

    Long getId();

    String getSku();

    String getNombre();

    String getCategoria();

    String getProveedor();

    Integer getStock();

    Integer getStockMinimo();

    Integer getPuntoReposicion();

    Integer getStockMaximo();

    BigDecimal getCostoPromedio();

    Double getPrecio();
}

package com.proyecto.microservicio.model;

import java.math.BigDecimal;

/** HU-17 y HU-20: producto con su valorización y sus umbrales de reposición. */
public interface ValorizacionProductoDTO {

    Long getProductoId();

    String getSku();

    String getProducto();

    String getCategoria();

    Integer getStock();

    Integer getStockMinimo();

    Integer getPuntoReposicion();

    BigDecimal getCostoPromedio();

    BigDecimal getValor();
}

package com.proyecto.microservicio.model;

/** HU-25: producto que alcanzo su umbral de reposicion. */
public interface StockCriticoDTO {

    Long getProductoId();

    String getSku();

    String getProducto();

    String getCategoria();

    Integer getStock();

    Integer getUmbral();

    Integer getStockMaximo();
}

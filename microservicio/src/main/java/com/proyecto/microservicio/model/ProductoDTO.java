package com.proyecto.microservicio.model;

public interface ProductoDTO {

    Long getId();

    String getNombre();

    String getDescripcion();

    Double getPrecio();

    Integer getStock();

    Integer getStockMinimo();

    String getCategoria();

    String getProveedor();
}
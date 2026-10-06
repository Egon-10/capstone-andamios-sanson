package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.model.Producto;

import java.math.BigDecimal;

/** HU-21: producto tal como aparece en una lista o en un resultado de búsqueda. */
public record ProductoResumen(
        Long id,
        String sku,
        String nombre,
        String categoria,
        String proveedor,
        Integer stock,
        Integer stockMinimo,
        Integer puntoReposicion,
        Double precio,
        BigDecimal costoPromedio,
        BigDecimal valorizado,
        boolean necesitaReposicion,
        boolean activo) {

    public static ProductoResumen de(Producto p) {
        return new ProductoResumen(
                p.getId(),
                p.getSku(),
                p.getNombre(),
                p.getCategoria() == null ? null : p.getCategoria().getNombre(),
                p.getProveedor() == null ? null : p.getProveedor().getNombre(),
                p.getStock(),
                p.getStockMinimo(),
                p.getPuntoReposicion(),
                p.getPrecio(),
                p.getCostoPromedio(),
                p.valorizado(),
                p.necesitaReposicion(),
                p.isActivo());
    }
}

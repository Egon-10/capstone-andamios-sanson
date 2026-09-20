package com.proyecto.microservicio.model;

public class DashboardDTO {

    private Long totalProductos;
    private Long totalCategorias;
    private Long totalProveedores;
    private Long totalUsuarios;
    private Integer stockTotal;

    public DashboardDTO() {
    }

    public DashboardDTO(
            Long totalProductos,
            Long totalCategorias,
            Long totalProveedores,
            Long totalUsuarios,
            Integer stockTotal) {

        this.totalProductos = totalProductos;
        this.totalCategorias = totalCategorias;
        this.totalProveedores = totalProveedores;
        this.totalUsuarios = totalUsuarios;
        this.stockTotal = stockTotal;
    }

    public Long getTotalProductos() {
        return totalProductos;
    }

    public void setTotalProductos(Long totalProductos) {
        this.totalProductos = totalProductos;
    }

    public Long getTotalCategorias() {
        return totalCategorias;
    }

    public void setTotalCategorias(Long totalCategorias) {
        this.totalCategorias = totalCategorias;
    }

    public Long getTotalProveedores() {
        return totalProveedores;
    }

    public void setTotalProveedores(Long totalProveedores) {
        this.totalProveedores = totalProveedores;
    }

    public Long getTotalUsuarios() {
        return totalUsuarios;
    }

    public void setTotalUsuarios(Long totalUsuarios) {
        this.totalUsuarios = totalUsuarios;
    }

    public Integer getStockTotal() {
        return stockTotal;
    }

    public void setStockTotal(Integer stockTotal) {
        this.stockTotal = stockTotal;
    }
}
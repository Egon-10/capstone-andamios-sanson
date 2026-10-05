package com.proyecto.microservicio.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código único del producto (HU-10, RF-10). */
    @Column(length = 20, unique = true)
    private String sku;

    private String nombre;

    private String descripcion;

    private Double precio;

    private Integer stock;

    @Column(name = "stock_minimo")
    private Integer stockMinimo;

    /** HU-20: nivel en el que se debe reponer. Por omisión, el stock mínimo. */
    @Column(name = "punto_reposicion")
    private Integer puntoReposicion;

    /** HU-20: tope del inventario, que acota cuánto tiene sentido comprar. */
    @Column(name = "stock_maximo")
    private Integer stockMaximo;

    /**
     * HU-17: costo promedio ponderado, recalculado en cada entrada. Se guarda
     * en la fila del producto para no recorrer todo el kardex cada vez que se
     * pide la valorización del inventario.
     */
    @Column(name = "costo_promedio", nullable = false, precision = 12, scale = 4)
    private BigDecimal costoPromedio = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activo = true;

    /**
     * HU-13: control de concurrencia optimista. Hibernate incrementa esta
     * columna en cada escritura y rechaza la que llegue con una versión
     * vencida, de modo que dos salidas simultáneas del mismo producto no
     * pueden dejar el stock en negativo: la segunda falla y se reintenta sobre
     * el stock ya actualizado.
     */
    @Version
    private Long version;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    public Producto() {
    }

    public Producto(Long id,
                    String nombre,
                    String descripcion,
                    Double precio,
                    Integer stock,
                    Integer stockMinimo,
                    Categoria categoria,
                    Proveedor proveedor) {

        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.stock = stock;
        this.stockMinimo = stockMinimo;
        this.categoria = categoria;
        this.proveedor = proveedor;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Double getPrecio() {
        return precio;
    }

    public Integer getStock() {
        return stock;
    }

    public Integer getStockMinimo() {
        return stockMinimo;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public void setStockMinimo(Integer stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public void setProveedor(Proveedor proveedor) {
        this.proveedor = proveedor;
    }

    /**
     * HU-20: el producto necesita reposición cuando el stock cae al punto de
     * reposición o por debajo. Si no se definió un punto de reposición se usa
     * el stock mínimo, y si tampoco hay mínimo no hay alerta que dar.
     */
    public boolean necesitaReposicion() {
        Integer umbral = puntoReposicion != null ? puntoReposicion : stockMinimo;
        return umbral != null && stock != null && stock <= umbral;
    }

    /** Valor del inventario de este producto: stock por costo promedio. */
    public BigDecimal valorizado() {
        if (costoPromedio == null || stock == null) {
            return BigDecimal.ZERO;
        }
        return costoPromedio.multiply(BigDecimal.valueOf(stock));
    }

    public Integer getPuntoReposicion() {
        return puntoReposicion;
    }

    public void setPuntoReposicion(Integer puntoReposicion) {
        this.puntoReposicion = puntoReposicion;
    }

    public Integer getStockMaximo() {
        return stockMaximo;
    }

    public void setStockMaximo(Integer stockMaximo) {
        this.stockMaximo = stockMaximo;
    }

    public BigDecimal getCostoPromedio() {
        return costoPromedio;
    }

    public void setCostoPromedio(BigDecimal costoPromedio) {
        this.costoPromedio = costoPromedio;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Long getVersion() {
        return version;
    }
}

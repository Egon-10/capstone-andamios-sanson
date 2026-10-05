package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.ProductoDTO;
import com.proyecto.microservicio.model.ValorizacionProductoDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository
        extends JpaRepository<Producto, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    @Query(value = """
    SELECT
        p.id AS id,
        p.sku AS sku,
        p.nombre AS nombre,
        p.descripcion AS descripcion,
        p.precio AS precio,
        p.stock AS stock,
        p.stock_minimo AS stockMinimo,
        c.nombre AS categoria,
        pr.nombre AS proveedor
    FROM productos p
    INNER JOIN categorias c
        ON p.categoria_id = c.id
    LEFT JOIN proveedores pr
        ON p.proveedor_id = pr.id
    """, nativeQuery = true)
    List<ProductoDTO> obtenerProductosConDetalle();

    @Query("""
SELECT COALESCE(SUM(p.stock),0)
FROM Producto p
""")
Integer obtenerStockTotal();

@Query("""
SELECT
c.nombre,
COUNT(p)
FROM Producto p
JOIN p.categoria c
GROUP BY c.nombre
""")
List<Object[]> productosPorCategoria();

@Query("""
SELECT
p.nombre,
p.stock
FROM Producto p
ORDER BY p.stock DESC
""")
List<Object[]> productosConMayorStock();

@Query("""
SELECT
p.nombre,
p.stock,
p.stockMinimo
FROM Producto p
WHERE p.stock <= p.stockMinimo
ORDER BY p.stock ASC
""")
List<Object[]> productosEnStockCritico();

@Query(value = """
SELECT
    p.id,
    p.nombre,
    c.nombre,
    pr.nombre,
    p.stock,
    p.stock_minimo
FROM productos p
INNER JOIN categorias c
    ON p.categoria_id = c.id
LEFT JOIN proveedores pr
    ON p.proveedor_id = pr.id
ORDER BY p.nombre
""", nativeQuery = true)
List<Object[]> obtenerReporteProductos();

@Query(value = """
SELECT
    p.id,
    p.nombre,
    c.nombre,
    p.stock,
    p.stock_minimo
FROM productos p
INNER JOIN categorias c
    ON p.categoria_id = c.id
WHERE
p.stock <= p.stock_minimo
AND
(
    :categoria IS NULL
    OR
    c.nombre = :categoria
)
ORDER BY p.stock ASC
""", nativeQuery = true)
List<Object[]> obtenerStockCritico(
        @Param("categoria")
        String categoria
);

@Query(value = """
SELECT
    p.id,
    p.nombre,
    c.nombre,
    pr.nombre,
    p.stock,
    p.stock_minimo
FROM productos p
INNER JOIN categorias c
    ON p.categoria_id = c.id
LEFT JOIN proveedores pr
    ON p.proveedor_id = pr.id
WHERE
(
    :categoria IS NULL
    OR
    c.nombre = :categoria
)
ORDER BY p.nombre
""", nativeQuery = true)
List<Object[]> obtenerReporteProductos(
        @Param("categoria")
        String categoria
);

    /**
     * HU-17: productos con su valorizacion al costo promedio ponderado.
     *
     * Se valoriza al costo y no al precio de venta: usar el precio
     * sobreestimaria el inventario en el margen comercial. Los productos dados
     * de baja quedan fuera, pero los de stock cero se incluyen para que el
     * conteo de productos del informe coincida con el catalogo activo.
     */
    @Query(value = """
    SELECT
        p.id AS productoId,
        p.sku AS sku,
        p.nombre AS producto,
        c.nombre AS categoria,
        p.stock AS stock,
        p.stock_minimo AS stockMinimo,
        p.punto_reposicion AS puntoReposicion,
        p.costo_promedio AS costoPromedio,
        ROUND(COALESCE(p.stock, 0) * COALESCE(p.costo_promedio, 0), 2) AS valor
    FROM productos p
    LEFT JOIN categorias c
        ON p.categoria_id = c.id
    WHERE p.activo = 1
    ORDER BY c.nombre, p.nombre
    """, nativeQuery = true)
    List<ValorizacionProductoDTO> obtenerValorizacion();

    /** HU-20: productos que alcanzaron su umbral de reposicion. */
    @Query(value = """
    SELECT
        p.id AS productoId,
        p.sku AS sku,
        p.nombre AS producto,
        c.nombre AS categoria,
        p.stock AS stock,
        p.stock_minimo AS stockMinimo,
        p.punto_reposicion AS puntoReposicion,
        p.costo_promedio AS costoPromedio,
        ROUND(COALESCE(p.stock, 0) * COALESCE(p.costo_promedio, 0), 2) AS valor
    FROM productos p
    LEFT JOIN categorias c
        ON p.categoria_id = c.id
    WHERE p.activo = 1
      AND COALESCE(p.punto_reposicion, p.stock_minimo) IS NOT NULL
      AND COALESCE(p.stock, 0) <= COALESCE(p.punto_reposicion, p.stock_minimo)
    ORDER BY COALESCE(p.stock, 0) ASC
    """, nativeQuery = true)
    List<ValorizacionProductoDTO> obtenerPorReponer();
}

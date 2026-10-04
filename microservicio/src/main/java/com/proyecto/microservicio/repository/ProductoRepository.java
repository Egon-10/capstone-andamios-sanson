package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.ProductoDTO;
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
    INNER JOIN proveedores pr
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
INNER JOIN proveedores pr
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
INNER JOIN proveedores pr
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
}


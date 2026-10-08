package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.ProductoDTO;
import com.proyecto.microservicio.model.ReporteProductoDTO;
import com.proyecto.microservicio.model.StockCriticoDTO;
import com.proyecto.microservicio.model.IndicadoresProductoDTO;
import com.proyecto.microservicio.model.ValorizacionCorteDTO;
import com.proyecto.microservicio.model.ValorizacionProductoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * HU-26 y HU-27: catalogo activo para el reporte de productos, con sus
     * umbrales y su costo. Opcionalmente acotado a una categoria.
     */
    @Query(value = """
    SELECT
        p.id AS id,
        p.sku AS sku,
        p.nombre AS nombre,
        c.nombre AS categoria,
        pr.nombre AS proveedor,
        p.stock AS stock,
        p.stock_minimo AS stockMinimo,
        p.punto_reposicion AS puntoReposicion,
        p.stock_maximo AS stockMaximo,
        p.costo_promedio AS costoPromedio,
        p.precio AS precio
    FROM productos p
    LEFT JOIN categorias c
        ON p.categoria_id = c.id
    LEFT JOIN proveedores pr
        ON p.proveedor_id = pr.id
    WHERE p.activo = 1
      AND (:categoriaId IS NULL OR p.categoria_id = :categoriaId)
    ORDER BY c.nombre, p.nombre
    """, nativeQuery = true)
    List<ReporteProductoDTO> obtenerParaReporte(@Param("categoriaId") Long categoriaId);

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

    /**
     * HU-28: valorizacion con corte a una fecha.
     *
     * El stock al corte se reconstruye como el stock actual menos el neto de
     * los movimientos posteriores, igual que el saldo inicial del kardex. Se
     * cuentan todos los asientos, anulados y compensaciones incluidos, porque
     * cada uno movio el stock en su momento y la compensacion se registra con
     * su propia fecha. El costo es el que dejo el ultimo movimiento anterior al
     * corte; el servicio decide que hacer cuando ese movimiento no lo guardo.
     *
     * El parametro corte es exclusivo: el primer instante del dia siguiente.
     */
    @Query(value = """
    SELECT
        p.id AS productoId,
        p.sku AS sku,
        p.nombre AS producto,
        c.nombre AS categoria,
        CAST(COALESCE(p.stock, 0) - COALESCE((
            SELECT SUM(m.cantidad * CASE WHEN m.tipo = 'ENTRADA' THEN 1 ELSE -1 END)
            FROM movimientos m
            WHERE m.producto_id = p.id
              AND m.fecha >= :corte
        ), 0) AS SIGNED) AS stock,
        p.stock_minimo AS stockMinimo,
        p.punto_reposicion AS puntoReposicion,
        (
            SELECT m2.costo_promedio_resultante
            FROM movimientos m2
            WHERE m2.producto_id = p.id
              AND m2.fecha < :corte
            ORDER BY m2.fecha DESC, m2.id DESC
            LIMIT 1
        ) AS costoHistorico,
        p.costo_promedio AS costoVigente,
        (
            SELECT COUNT(*)
            FROM movimientos m3
            WHERE m3.producto_id = p.id
              AND m3.fecha >= :corte
        ) AS movimientosPosteriores
    FROM productos p
    LEFT JOIN categorias c
        ON p.categoria_id = c.id
    WHERE p.activo = 1
    ORDER BY c.nombre, p.nombre
    """, nativeQuery = true)
    List<ValorizacionCorteDTO> obtenerValorizacionAlCorte(@Param("corte") LocalDateTime corte);

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

    /**
     * HU-21: busqueda paginada en el servidor.
     *
     * La paginacion se hace en la base y no en el cliente: con el catalogo
     * completo en memoria, el filtrado funcionaba solo mientras el catalogo era
     * pequeno. El texto se compara contra el nombre y contra el SKU, en
     * minusculas, de modo que buscar "pla-200" o "Plataforma" encuentre lo
     * mismo.
     *
     * Cada filtro se anula a si mismo cuando su parametro viene vacio, lo que
     * permite una sola consulta en lugar de una combinacion por cada caso.
     */
    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN p.categoria c
            LEFT JOIN p.proveedor pr
            WHERE (:texto IS NULL
                   OR LOWER(p.nombre) LIKE :texto
                   OR LOWER(p.sku) LIKE :texto)
              AND (:categoriaId IS NULL OR c.id = :categoriaId)
              AND (:proveedorId IS NULL OR pr.id = :proveedorId)
              AND (:soloActivos = FALSE OR p.activo = TRUE)
              AND (:porReponer = FALSE
                   OR (COALESCE(p.puntoReposicion, p.stockMinimo) IS NOT NULL
                       AND p.stock <= COALESCE(p.puntoReposicion, p.stockMinimo)))
            """)
    Page<Producto> buscar(@Param("texto") String texto,
                          @Param("categoriaId") Long categoriaId,
                          @Param("proveedorId") Long proveedorId,
                          @Param("soloActivos") boolean soloActivos,
                          @Param("porReponer") boolean porReponer,
                          Pageable paginacion);

    /**
     * HU-23: agregados del catalogo activo. Las sumas se convierten a entero
     * con CAST porque MySQL devuelve DECIMAL al sumar columnas enteras.
     */
    @Query(value = """
    SELECT
        COUNT(*) AS productosActivos,
        CAST(COALESCE(SUM(COALESCE(p.stock, 0)), 0) AS SIGNED) AS unidades,
        COALESCE(ROUND(SUM(COALESCE(p.stock, 0) * COALESCE(p.costo_promedio, 0)), 2), 0) AS valor,
        CAST(COALESCE(SUM(CASE
            WHEN COALESCE(p.punto_reposicion, p.stock_minimo) IS NOT NULL
             AND COALESCE(p.stock, 0) <= COALESCE(p.punto_reposicion, p.stock_minimo)
            THEN 1 ELSE 0 END), 0) AS SIGNED) AS porReponer,
        CAST(COALESCE(SUM(CASE WHEN COALESCE(p.stock, 0) <= 0 THEN 1 ELSE 0 END), 0) AS SIGNED) AS sinStock
    FROM productos p
    WHERE p.activo = 1
    """, nativeQuery = true)
    IndicadoresProductoDTO obtenerIndicadores();

    /** HU-25: productos activos que alcanzaron su umbral de reposicion. */
    @Query(value = """
    SELECT
        p.id AS productoId,
        p.sku AS sku,
        p.nombre AS producto,
        c.nombre AS categoria,
        COALESCE(p.stock, 0) AS stock,
        COALESCE(p.punto_reposicion, p.stock_minimo) AS umbral,
        p.stock_maximo AS stockMaximo
    FROM productos p
    LEFT JOIN categorias c
        ON p.categoria_id = c.id
    WHERE p.activo = 1
      AND COALESCE(p.punto_reposicion, p.stock_minimo) IS NOT NULL
      AND COALESCE(p.stock, 0) <= COALESCE(p.punto_reposicion, p.stock_minimo)
    """, nativeQuery = true)
    List<StockCriticoDTO> obtenerStockCritico();
}

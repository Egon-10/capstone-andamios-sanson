package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Movimiento;
import com.proyecto.microservicio.model.KardexLineaDTO;
import com.proyecto.microservicio.model.MovimientoDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoRepository
        extends JpaRepository<Movimiento, Long> {

    @Query(value = """
    SELECT
        m.id AS id,
        m.tipo AS tipo,
        m.motivo AS motivo,
        mo.nombre AS motivoNombre,
        m.observacion AS observacion,
        m.cantidad AS cantidad,
        m.fecha AS fecha,
        m.estado AS estado,
        m.costo_unitario AS costoUnitario,
        m.saldo_resultante AS saldoResultante,
        p.id AS productoId,
        p.nombre AS producto,
        p.sku AS sku,
        TRIM(CONCAT(COALESCE(u.nombre, ''), ' ', COALESCE(u.apellidos, ''))) AS usuario,
        m.movimiento_origen_id AS movimientoOrigenId
    FROM movimientos m
    INNER JOIN productos p
        ON m.producto_id = p.id
    LEFT JOIN usuarios u
        ON m.usuario_id = u.id
    LEFT JOIN motivos_movimiento mo
        ON m.motivo = mo.codigo
    ORDER BY m.fecha DESC, m.id DESC
    """, nativeQuery = true)
    List<MovimientoDTO> obtenerMovimientosConDetalle();

    @Query("""
SELECT
m.tipo,
COUNT(m)
FROM Movimiento m
GROUP BY m.tipo
""")
List<Object[]> resumenMovimientos();

@Query(value = """
SELECT
DATE(m.fecha) AS dia,
SUM(CASE WHEN m.tipo = 'ENTRADA' THEN 1 ELSE 0 END) AS entradas,
SUM(CASE WHEN m.tipo = 'SALIDA' THEN 1 ELSE 0 END) AS salidas
FROM movimientos m
GROUP BY DATE(m.fecha)
ORDER BY DATE(m.fecha)
""", nativeQuery = true)
List<Object[]> movimientosPorDia();


@Query(value = """
SELECT
    DAYOFWEEK(m.fecha) AS dia,
    m.tipo AS tipo,
    COUNT(*) AS total
FROM movimientos m
WHERE YEARWEEK(m.fecha,1) = YEARWEEK(CURDATE(),1) + :offset
GROUP BY DAYOFWEEK(m.fecha), m.tipo
ORDER BY DAYOFWEEK(m.fecha)
""", nativeQuery = true)
List<Object[]> movimientosPorSemana(
        Integer offset);

    @Query(value = """
    SELECT
        m.id AS id,
        m.tipo AS tipo,
        m.motivo AS motivo,
        mo.nombre AS motivoNombre,
        m.observacion AS observacion,
        m.cantidad AS cantidad,
        m.fecha AS fecha,
        m.estado AS estado,
        m.costo_unitario AS costoUnitario,
        m.saldo_resultante AS saldoResultante,
        p.id AS productoId,
        p.nombre AS producto,
        p.sku AS sku,
        TRIM(CONCAT(COALESCE(u.nombre, ''), ' ', COALESCE(u.apellidos, ''))) AS usuario,
        m.movimiento_origen_id AS movimientoOrigenId
    FROM movimientos m
    INNER JOIN productos p
        ON m.producto_id = p.id
    LEFT JOIN usuarios u
        ON m.usuario_id = u.id
    LEFT JOIN motivos_movimiento mo
        ON m.motivo = mo.codigo
    WHERE
        (:fechaInicio IS NULL OR m.fecha >= :fechaInicio)
    AND
        (:fechaFin IS NULL OR m.fecha <= :fechaFin)
    AND
        (:tipo IS NULL OR m.tipo = :tipo)
    ORDER BY m.fecha DESC, m.id DESC
    """, nativeQuery = true)
    List<MovimientoDTO> buscarConFiltros(
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            @Param("tipo") String tipo);

    @Query(value = """
    SELECT
        m.id AS id,
        m.tipo AS tipo,
        m.motivo AS motivo,
        mo.nombre AS motivoNombre,
        m.observacion AS observacion,
        m.cantidad AS cantidad,
        m.fecha AS fecha,
        m.estado AS estado,
        m.costo_unitario AS costoUnitario,
        m.saldo_resultante AS saldoResultante,
        p.id AS productoId,
        p.nombre AS producto,
        p.sku AS sku,
        TRIM(CONCAT(COALESCE(u.nombre, ''), ' ', COALESCE(u.apellidos, ''))) AS usuario,
        m.movimiento_origen_id AS movimientoOrigenId
    FROM movimientos m
    INNER JOIN productos p
        ON m.producto_id = p.id
    LEFT JOIN usuarios u
        ON m.usuario_id = u.id
    LEFT JOIN motivos_movimiento mo
        ON m.motivo = mo.codigo
    WHERE m.tipo = :tipo
    ORDER BY m.fecha DESC, m.id DESC
    """, nativeQuery = true)
    List<MovimientoDTO> obtenerMovimientosPorTipo(@Param("tipo") String tipo);

    /**
     * HU-18: asientos de un producto con el acumulado de su efecto sobre el
     * stock. La funcion de ventana calcula la suma corrida en la propia base,
     * que es mucho mas eficiente que traer los asientos y acumularlos en Java.
     *
     * El acumulado parte de cero en el primer asiento: el servicio le suma el
     * saldo inicial del producto para obtener el saldo real de cada linea.
     */
    @Query(value = """
    SELECT
        m.id AS id,
        m.fecha AS fecha,
        m.tipo AS tipo,
        m.motivo AS motivo,
        mo.nombre AS motivoNombre,
        m.observacion AS observacion,
        m.estado AS estado,
        m.cantidad AS cantidad,
        m.costo_unitario AS costoUnitario,
        TRIM(CONCAT(COALESCE(u.nombre, ''), ' ', COALESCE(u.apellidos, ''))) AS usuario,
        SUM(m.cantidad * CASE WHEN m.tipo = 'ENTRADA' THEN 1 ELSE -1 END)
            OVER (ORDER BY m.fecha, m.id) AS acumulado
    FROM movimientos m
    LEFT JOIN usuarios u
        ON m.usuario_id = u.id
    LEFT JOIN motivos_movimiento mo
        ON m.motivo = mo.codigo
    WHERE m.producto_id = :productoId
      AND (:desde IS NULL OR m.fecha >= :desde)
      AND (:hasta IS NULL OR m.fecha <= :hasta)
    ORDER BY m.fecha, m.id
    """, nativeQuery = true)
    List<KardexLineaDTO> obtenerKardex(@Param("productoId") Long productoId,
                                       @Param("desde") LocalDateTime desde,
                                       @Param("hasta") LocalDateTime hasta);

    /**
     * Neto de todos los asientos de un producto: entradas menos salidas. El
     * saldo inicial del kardex es el stock actual menos este neto.
     */
    @Query(value = """
    SELECT COALESCE(SUM(m.cantidad * CASE WHEN m.tipo = 'ENTRADA' THEN 1 ELSE -1 END), 0)
    FROM movimientos m
    WHERE m.producto_id = :productoId
    """, nativeQuery = true)
    Integer obtenerNeto(@Param("productoId") Long productoId);

    /**
     * Neto de los asientos anteriores a una fecha. Lo necesita el kardex
     * filtrado por rango para saber con que saldo arranca la primera linea.
     */
    @Query(value = """
    SELECT COALESCE(SUM(m.cantidad * CASE WHEN m.tipo = 'ENTRADA' THEN 1 ELSE -1 END), 0)
    FROM movimientos m
    WHERE m.producto_id = :productoId
      AND m.fecha < :desde
    """, nativeQuery = true)
    Integer obtenerNetoAntesDe(@Param("productoId") Long productoId,
                               @Param("desde") LocalDateTime desde);
}

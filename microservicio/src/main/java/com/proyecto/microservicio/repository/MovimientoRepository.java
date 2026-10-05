package com.proyecto.microservicio.repository;

import com.proyecto.microservicio.model.Movimiento;
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
}

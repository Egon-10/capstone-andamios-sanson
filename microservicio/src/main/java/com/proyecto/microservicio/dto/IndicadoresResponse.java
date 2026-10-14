package com.proyecto.microservicio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HU-23: panel de indicadores de gestion del inventario.
 *
 * Reune en una sola respuesta las cifras que el gerente revisa al empezar el
 * dia. Todas se calculan sobre el catalogo activo y sobre los movimientos
 * vigentes: un producto dado de baja no suma unidades ni valor, y un
 * movimiento anulado no cuenta como actividad del dia.
 */
public record IndicadoresResponse(
        long productosActivos,
        long unidadesEnStock,
        BigDecimal valorInventario,
        long productosPorReponer,
        long productosSinStock,
        long movimientosHoy,
        long entradasHoy,
        long salidasHoy,
        long ajustesPendientes,
        LocalDateTime fechaCorte) {
}

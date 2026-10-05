package com.proyecto.microservicio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HU-22: ficha de detalle consolidada de un producto.
 *
 * Reúne en una sola respuesta lo que antes exigía cuatro consultas desde el
 * cliente: los datos del producto, su valorización, sus umbrales, el resumen
 * de su movimiento y los últimos asientos de su kardex.
 */
public record FichaProductoResponse(
        Long id,
        String sku,
        String nombre,
        String descripcion,
        boolean activo,
        Referencia categoria,
        Referencia proveedor,
        Existencias existencias,
        Valorizacion valorizacion,
        Resumen resumen,
        List<KardexResponse.Linea> ultimosMovimientos,
        int ajustesPendientes) {

    public record Referencia(Long id, String nombre) {
    }

    /** Stock actual frente a los umbrales configurados (HU-20). */
    public record Existencias(
            Integer stock,
            Integer stockMinimo,
            Integer puntoReposicion,
            Integer stockMaximo,
            boolean necesitaReposicion,
            Integer faltanteHastaElMaximo) {
    }

    public record Valorizacion(
            Double precioVenta,
            BigDecimal costoPromedio,
            BigDecimal valorizado) {
    }

    /** Totales del histórico del producto. */
    public record Resumen(
            long movimientos,
            long entradas,
            long salidas,
            Integer unidadesIngresadas,
            Integer unidadesRetiradas,
            LocalDateTime ultimoMovimiento) {
    }
}

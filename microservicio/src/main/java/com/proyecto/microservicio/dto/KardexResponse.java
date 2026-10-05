package com.proyecto.microservicio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** HU-18: kardex de un producto con el saldo acumulado por movimiento. */
public record KardexResponse(
        Long productoId,
        String sku,
        String producto,
        Integer stockActual,
        BigDecimal costoPromedio,
        BigDecimal valorizado,
        Integer saldoInicial,
        List<Linea> lineas) {

    /**
     * Una línea del kardex. El saldo es el del producto después del asiento, y
     * el valorizado es cantidad por costo unitario del propio asiento.
     */
    public record Linea(
            Long id,
            LocalDateTime fecha,
            String tipo,
            String motivo,
            String motivoNombre,
            String observacion,
            String estado,
            Integer entrada,
            Integer salida,
            BigDecimal costoUnitario,
            BigDecimal valorizado,
            Integer saldo,
            String usuario) {
    }
}

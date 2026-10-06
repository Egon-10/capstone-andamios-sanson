package com.proyecto.microservicio.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Proyección de un movimiento con los datos que necesita la tabla de
 * movimientos: el producto y el usuario resueltos a su nombre, más el motivo,
 * el estado y el saldo que incorporó el Sprint 2.
 */
public interface MovimientoDTO {

    Long getId();

    String getTipo();

    /** HU-14: código del motivo tipificado. */
    String getMotivo();

    /** Nombre legible del motivo, para mostrar en la tabla. */
    String getMotivoNombre();

    String getObservacion();

    Integer getCantidad();

    LocalDateTime getFecha();

    /** HU-15: REGISTRADO, ANULADO o COMPENSACION. */
    String getEstado();

    BigDecimal getCostoUnitario();

    /** HU-18: saldo del producto después del asiento. Vacío en los heredados. */
    Integer getSaldoResultante();

    Long getProductoId();

    String getProducto();

    String getSku();

    String getUsuario();

    /** HU-15: asiento al que compensa, cuando es una anulación. */
    Long getMovimientoOrigenId();
}

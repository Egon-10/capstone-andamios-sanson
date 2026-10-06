package com.proyecto.microservicio.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HU-18: línea del kardex de un producto, con el acumulado del movimiento.
 *
 * El acumulado que devuelve la consulta parte de cero en el primer asiento. El
 * saldo real se obtiene sumándole el saldo inicial del producto, que el
 * servicio calcula como el stock actual menos el neto de todos los asientos.
 * Se hace así porque los movimientos heredados de la base anterior no
 * registraron su saldo, y reconstruirlo hacia atrás desde el stock actual es
 * la única forma de que la columna cuadre con el inventario real.
 */
public interface KardexLineaDTO {

    Long getId();

    LocalDateTime getFecha();

    String getTipo();

    String getMotivo();

    String getMotivoNombre();

    String getObservacion();

    String getEstado();

    Integer getCantidad();

    BigDecimal getCostoUnitario();

    String getUsuario();

    /** Suma de los efectos sobre el stock desde el primer asiento hasta este. */
    Integer getAcumulado();
}

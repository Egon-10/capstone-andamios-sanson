package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * HU-12 y HU-13: registro de una entrada o una salida.
 *
 * No incluye el identificador del usuario a propósito: quien registra el
 * movimiento se toma del token de la sesión (CR-04). El esquema anterior lo
 * recibía como parámetro de la solicitud, lo que permitía atribuir un
 * movimiento a otra persona.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MovimientoRequest(

        @NotNull(message = "Seleccione el producto")
        Long productoId,

        @NotNull(message = "Ingrese la cantidad")
        @Positive(message = "La cantidad debe ser mayor que cero")
        Integer cantidad,

        @NotBlank(message = "Seleccione el motivo del movimiento")
        @Size(max = 30, message = "El motivo admite hasta 30 caracteres")
        String motivo,

        @Size(max = 200, message = "La observación admite hasta 200 caracteres")
        String observacion,

        /**
         * Costo unitario de la mercadería que ingresa. Solo se usa en las
         * entradas, donde alimenta el promedio ponderado (HU-17). Si no se
         * informa, la entrada se valoriza al costo promedio vigente del
         * producto, que es lo que corresponde a una devolución o un traslado.
         * En las salidas se ignora: el costo lo fija el promedio del producto.
         */
        @PositiveOrZero(message = "El costo unitario no puede ser negativo")
        BigDecimal costoUnitario) {
}

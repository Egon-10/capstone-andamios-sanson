package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * HU-20: umbrales de reposición de un producto.
 *
 * Se editan por separado del resto de la ficha porque los define quien
 * planifica las compras, no quien da de alta el producto, y porque cambiarlos
 * no debe obligar a reenviar todos los demás campos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UmbralesRequest(

        @PositiveOrZero(message = "El stock mínimo no puede ser negativo")
        Integer stockMinimo,

        @PositiveOrZero(message = "El punto de reposición no puede ser negativo")
        Integer puntoReposicion,

        @PositiveOrZero(message = "El stock máximo no puede ser negativo")
        Integer stockMaximo) {
}

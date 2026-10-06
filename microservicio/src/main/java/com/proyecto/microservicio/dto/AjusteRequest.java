package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * HU-16: registro de un conteo físico.
 *
 * Solo se informa lo que se contó. El stock que el sistema tiene registrado lo
 * lee el servidor en el momento de guardar: si el cliente lo enviara, podría
 * declarar una diferencia distinta de la real.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AjusteRequest(

        @NotNull(message = "Seleccione el producto")
        Long productoId,

        @NotNull(message = "Ingrese las unidades contadas")
        @PositiveOrZero(message = "Las unidades contadas no pueden ser negativas")
        Integer stockFisico,

        @Size(max = 200, message = "La observación admite hasta 200 caracteres")
        String observacion) {
}

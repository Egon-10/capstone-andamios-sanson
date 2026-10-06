package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-16: rechazo de un conteo físico, con el motivo por el que no se acepta. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RechazoRequest(

        @NotBlank(message = "Explique por qué se rechaza el ajuste")
        @Size(min = 10, max = 200, message = "El motivo debe tener entre 10 y 200 caracteres")
        String motivo) {
}

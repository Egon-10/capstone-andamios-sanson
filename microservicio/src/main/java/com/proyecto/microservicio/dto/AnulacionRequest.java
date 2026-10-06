package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HU-15: anulación de un movimiento. Se exige una justificación escrita porque
 * la anulación corrige el inventario y debe quedar explicada en el kardex.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnulacionRequest(

        @NotBlank(message = "Explique por qué se anula el movimiento")
        @Size(min = 10, max = 200, message = "La justificación debe tener entre 10 y 200 caracteres")
        String justificacion) {
}

package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-11: alta o edicion de una categoria del catalogo maestro. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoriaRequest(

        @NotBlank(message = "Ingrese el nombre de la categoría")
        @Size(max = 60, message = "El nombre admite hasta 60 caracteres")
        String nombre,

        @Size(max = 200, message = "La descripción admite hasta 200 caracteres")
        String descripcion) {
}

package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** HU-10: registro de producto con validación declarativa en el servidor. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoRequest(

        @NotBlank(message = "Ingrese el código SKU")
        @Pattern(regexp = "^[A-Za-z0-9-]{3,20}$", message = "El SKU debe tener entre 3 y 20 caracteres: letras, números o guiones")
        String sku,

        @NotBlank(message = "Ingrese el nombre del producto")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
        String nombre,

        @Size(max = 250, message = "La descripción admite hasta 250 caracteres")
        String descripcion,

        @NotNull(message = "Ingrese el precio")
        @PositiveOrZero(message = "El precio no puede ser negativo")
        Double precio,

        @NotNull(message = "Ingrese el stock")
        @PositiveOrZero(message = "El stock no puede ser negativo")
        Integer stock,

        @NotNull(message = "Ingrese el stock mínimo")
        @PositiveOrZero(message = "El stock mínimo no puede ser negativo")
        Integer stockMinimo,

        @NotNull(message = "Seleccione una categoría")
        @Valid
        Referencia categoria,

        @Valid
        Referencia proveedor) {

    /** Referencia a otra entidad por su identificador: { "id": 3 }. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Referencia(@NotNull(message = "El identificador es obligatorio") Long id) {
    }
}

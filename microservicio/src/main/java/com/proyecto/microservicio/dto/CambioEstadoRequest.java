package com.proyecto.microservicio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * HU-31: activación o desactivación de una cuenta. El motivo es obligatorio
 * al desactivar: queda en la bitácora para explicar por qué alguien perdió el
 * acceso.
 */
public record CambioEstadoRequest(
        @NotBlank(message = "Indique el estado")
        @Pattern(regexp = "ACTIVO|INACTIVO", message = "Estado no válido")
        String estado,

        @Size(max = 200, message = "El motivo admite hasta 200 caracteres")
        String motivo) {
}

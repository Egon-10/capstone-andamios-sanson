package com.proyecto.microservicio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HU-37: cambio de la contraseña propia. Exige la actual para que una sesión
 * abierta y desatendida no baste para apropiarse de la cuenta.
 */
public record CambioPasswordRequest(
        @NotBlank(message = "Ingrese su contraseña actual")
        String actual,

        @NotBlank(message = "Ingrese la nueva contraseña")
        @Size(max = 72, message = "La contraseña admite hasta 72 caracteres")
        String nueva,

        @NotBlank(message = "Confirme la nueva contraseña")
        String confirmacion) {
}

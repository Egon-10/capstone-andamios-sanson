package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/** Credenciales de inicio de sesión. "correo" acepta el correo o el nombre de usuario. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(
        @NotBlank(message = "Ingrese su correo o nombre de usuario") String correo,
        @NotBlank(message = "Ingrese su contraseña") String password) {
}

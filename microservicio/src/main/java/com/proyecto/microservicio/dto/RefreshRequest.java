package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RefreshRequest(
        @NotBlank(message = "El token de renovación es obligatorio") String refreshToken) {
}

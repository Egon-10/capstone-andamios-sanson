package com.proyecto.microservicio.dto;

/** Respuesta del inicio de sesión y de la renovación de la sesión. */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tipo,
        long expiraEnSegundos,
        long inactividadMaximaSegundos,
        UsuarioResponse usuario) {
}

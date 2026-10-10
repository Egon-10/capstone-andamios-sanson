package com.proyecto.microservicio.dto;

/**
 * HU-36: contraseña temporal generada por el restablecimiento. Se muestra una
 * sola vez al administrador; el servidor solo guarda su hash.
 */
public record RestablecimientoResponse(String nombreUsuario, String passwordTemporal) {
}

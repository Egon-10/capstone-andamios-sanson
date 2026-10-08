package com.proyecto.microservicio.dto;

import java.time.LocalDateTime;

/**
 * HU-34: perfil del usuario autenticado.
 *
 * Además de sus datos, muestra su acceso anterior y los intentos fallidos
 * desde entonces: si alguien intentó entrar con su cuenta, el propio usuario
 * es quien mejor puede notarlo.
 *
 * @param accesoAnterior          fecha del inicio de sesión exitoso anterior al actual
 * @param fallidosDesdeAnterior   intentos fallidos registrados desde ese acceso
 */
public record PerfilResponse(
        UsuarioResponse usuario,
        LocalDateTime accesoAnterior,
        long fallidosDesdeAnterior) {
}

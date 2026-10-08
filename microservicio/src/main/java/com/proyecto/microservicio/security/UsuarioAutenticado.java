package com.proyecto.microservicio.security;

import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Lee la identidad del usuario desde el token de la sesión.
 *
 * CR-04 exige que la identidad de quien ejecuta una operación se tome siempre
 * del token y nunca de la solicitud, de modo que nadie pueda atribuir a otro
 * usuario un movimiento, un ajuste o una anulación. Esta clase es el único
 * punto por el que se obtiene ese dato, para que ningún controlador vuelva a
 * aceptar un identificador de usuario enviado por el cliente.
 */
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    /** Identificador del usuario de la sesión, que viaja en el subject del token. */
    public static Long id(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new CredencialesInvalidasException("La sesión no es válida");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new CredencialesInvalidasException("La sesión no es válida");
        }
    }

    /**
     * Usuario de la solicitud en curso, leído del contexto de seguridad. Lo usan
     * los servicios que registran auditoría sin recibir el token como
     * parámetro. Vacío fuera de una solicitud autenticada (por ejemplo, en una
     * tarea del propio servidor).
     */
    public static Optional<Long> actual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof Jwt jwt) {
            try {
                return Optional.of(id(jwt));
            } catch (CredencialesInvalidasException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}

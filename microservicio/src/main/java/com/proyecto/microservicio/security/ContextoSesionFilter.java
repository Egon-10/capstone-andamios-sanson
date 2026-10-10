package com.proyecto.microservicio.security;

import com.proyecto.microservicio.config.SolicitudFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Se ejecuta después de validar el token.
 *
 * HU-41: pone el usuario de la sesión en el contexto de los registros, para
 * que cada línea del servidor diga quién originó la operación.
 *
 * HU-36: una sesión abierta con contraseña temporal solo puede cambiar la
 * contraseña, consultar el propio perfil y cerrar sesión. La regla se aplica
 * en el servidor: ocultar las pantallas en el cliente no bastaría, porque el
 * token serviría igual para llamar a la API.
 */
public class ContextoSesionFilter extends OncePerRequestFilter {

    static final Set<String> PERMITIDAS_CON_TEMPORAL = Set.of(
            "/api/auth/cambio-password", "/api/auth/logout", "/api/usuarios/me");

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof Jwt jwt) {
            MDC.put(SolicitudFilter.CLAVE_USUARIO, jwt.getSubject());
            if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtService.CLAIM_CONTRASENA_TEMPORAL))
                    && !PERMITIDAS_CON_TEMPORAL.contains(solicitud.getRequestURI())) {
                RespuestaJson.error(respuesta, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                        "Debe cambiar su contraseña temporal antes de continuar.");
                return;
            }
        }
        cadena.doFilter(solicitud, respuesta);
    }
}

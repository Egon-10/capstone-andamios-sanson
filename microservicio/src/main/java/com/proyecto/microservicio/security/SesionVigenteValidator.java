package com.proyecto.microservicio.security;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

/**
 * HU-31: rechaza el token de una cuenta desactivada o cuyas sesiones fueron
 * cerradas por el servidor.
 *
 * Un JWT es válido hasta su vencimiento aunque la cuenta cambie, porque el
 * servidor no guarda estado de sesión. Este validador consulta la cuenta en
 * cada solicitud: es una lectura por clave primaria, que para el volumen de
 * usuarios de la empresa no tiene costo apreciable, y a cambio una
 * desactivación surte efecto en la siguiente solicitud y no quince minutos
 * después.
 */
public class SesionVigenteValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error CUENTA_INACTIVA =
            new OAuth2Error("invalid_token", "La cuenta está desactivada", null);
    private static final OAuth2Error SESION_CERRADA =
            new OAuth2Error("invalid_token", "La sesión fue cerrada por el servidor", null);

    private final UsuarioRepository usuarios;

    public SesionVigenteValidator(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Optional<Usuario> usuario = buscar(token.getSubject());
        if (usuario.isEmpty() || !usuario.get().estaActivo()) {
            return OAuth2TokenValidatorResult.failure(CUENTA_INACTIVA);
        }
        if (emitidoAntesDelCierre(token.getIssuedAt(), usuario.get())) {
            return OAuth2TokenValidatorResult.failure(SESION_CERRADA);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private Optional<Usuario> buscar(String subject) {
        try {
            return subject == null ? Optional.empty() : usuarios.findById(Long.valueOf(subject));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static boolean emitidoAntesDelCierre(Instant emitido, Usuario usuario) {
        if (usuario.getSesionesValidasDesde() == null) {
            return false;
        }
        Instant cierre = usuario.getSesionesValidasDesde().atZone(ZonaHoraria.NEGOCIO).toInstant();
        return emitido == null || emitido.isBefore(cierre);
    }
}

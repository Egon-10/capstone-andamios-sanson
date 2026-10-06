package com.proyecto.microservicio.security;

import com.proyecto.microservicio.repository.TokenRevocadoRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** HU-08: rechaza los tokens de acceso que fueron invalidados al cerrar sesión. */
public class TokenNoRevocadoValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error TOKEN_REVOCADO =
            new OAuth2Error("invalid_token", "La sesión fue cerrada", null);

    private final TokenRevocadoRepository repositorio;

    public TokenNoRevocadoValidator(TokenRevocadoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String jti = token.getId();
        if (jti != null && repositorio.existsById(jti)) {
            return OAuth2TokenValidatorResult.failure(TOKEN_REVOCADO);
        }
        return OAuth2TokenValidatorResult.success();
    }
}

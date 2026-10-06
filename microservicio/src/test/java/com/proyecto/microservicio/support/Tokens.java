package com.proyecto.microservicio.support;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

/** Tokens de prueba: el subject es el identificador del usuario de la sesion. */
public final class Tokens {

    private Tokens() {
    }

    public static Jwt deUsuario(long id) {
        return Jwt.withTokenValue("token-de-prueba")
                .header("alg", "HS256")
                .subject(String.valueOf(id))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }
}

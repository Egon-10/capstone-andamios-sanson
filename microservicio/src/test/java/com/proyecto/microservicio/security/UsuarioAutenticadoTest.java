package com.proyecto.microservicio.security;

import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import com.proyecto.microservicio.support.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.*;

/** CR-04: la identidad de quien opera se toma del token y de ningun otro lado. */
class UsuarioAutenticadoTest {

    @Test
    @DisplayName("Lee el identificador del usuario desde el subject del token")
    void leeElSubject() {
        assertEquals(7L, UsuarioAutenticado.id(Tokens.deUsuario(7)));
    }

    @Test
    @DisplayName("Rechaza una sesion sin token")
    void sinToken() {
        assertThrows(CredencialesInvalidasException.class, () -> UsuarioAutenticado.id(null));
    }

    @Test
    @DisplayName("Rechaza un subject que no es un identificador numerico")
    void subjectInvalido() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("admin").build();
        assertThrows(CredencialesInvalidasException.class, () -> UsuarioAutenticado.id(jwt));
    }

    @Test
    @DisplayName("Rechaza un token sin subject")
    void sinSubject() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").claim("roles", "X").build();
        assertThrows(CredencialesInvalidasException.class, () -> UsuarioAutenticado.id(jwt));
    }
}

package com.proyecto.microservicio.security;

import com.proyecto.microservicio.repository.TokenRevocadoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CP-08: un token invalidado al cerrar sesión no vuelve a ser aceptado. */
class TokenNoRevocadoValidatorTest {

    private final TokenRevocadoRepository repositorio = mock(TokenRevocadoRepository.class);
    private final TokenNoRevocadoValidator validador = new TokenNoRevocadoValidator(repositorio);

    private static Jwt token(String jti) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject("1").jti(jti).build();
    }

    @Test
    @DisplayName("CP-08: rechaza un token cuya sesión fue cerrada")
    void rechazaTokenRevocado() {
        when(repositorio.existsById("jti-cerrado")).thenReturn(true);

        assertTrue(validador.validate(token("jti-cerrado")).hasErrors());
    }

    @Test
    @DisplayName("Acepta un token vigente")
    void aceptaTokenVigente() {
        when(repositorio.existsById("jti-vigente")).thenReturn(false);

        assertFalse(validador.validate(token("jti-vigente")).hasErrors());
    }
}

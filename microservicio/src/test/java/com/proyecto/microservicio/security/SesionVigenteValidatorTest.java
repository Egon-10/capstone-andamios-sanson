package com.proyecto.microservicio.security;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CP-32: la desactivación de una cuenta surte efecto sobre las sesiones abiertas (HU-31). */
class SesionVigenteValidatorTest {

    private UsuarioRepository usuarios;
    private SesionVigenteValidator validador;
    private Usuario luis;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        validador = new SesionVigenteValidator(usuarios);
        luis = new Usuario();
        luis.setId(2L);
        luis.setEstado(Usuario.ESTADO_ACTIVO);
        when(usuarios.findById(2L)).thenReturn(Optional.of(luis));
    }

    private static Jwt token(String subject, Instant emitido) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(subject)
                .issuedAt(emitido).expiresAt(emitido.plusSeconds(900)).build();
    }

    @Test
    @DisplayName("CP-32: acepta el token de una cuenta activa sin cierre de sesiones")
    void cuentaActiva() {
        assertFalse(validador.validate(token("2", Instant.now())).hasErrors());
    }

    @Test
    @DisplayName("CP-32: rechaza el token de una cuenta desactivada aunque no haya vencido")
    void cuentaDesactivada() {
        luis.setEstado(Usuario.ESTADO_INACTIVO);
        assertTrue(validador.validate(token("2", Instant.now())).hasErrors());
    }

    @Test
    @DisplayName("CP-32: rechaza un token emitido antes del cierre de sesiones")
    void emitidoAntesDelCierre() {
        LocalDateTime cierre = ZonaHoraria.ahora().withNano(0);
        luis.setSesionesValidasDesde(cierre);
        Instant antes = cierre.atZone(ZonaHoraria.NEGOCIO).toInstant().minusSeconds(60);
        Instant despues = cierre.atZone(ZonaHoraria.NEGOCIO).toInstant().plusSeconds(1);

        assertTrue(validador.validate(token("2", antes)).hasErrors());
        assertFalse(validador.validate(token("2", despues)).hasErrors());
    }

    @Test
    @DisplayName("Rechaza un token de un usuario inexistente o con subject inválido")
    void usuarioInexistente() {
        assertTrue(validador.validate(token("99", Instant.now())).hasErrors());
        assertTrue(validador.validate(token("abc", Instant.now())).hasErrors());
    }
}

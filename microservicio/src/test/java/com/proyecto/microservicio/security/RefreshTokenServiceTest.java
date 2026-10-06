package com.proyecto.microservicio.security;

import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import com.proyecto.microservicio.model.RefreshToken;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HU-03 y HU-09: rotación y expiración por inactividad del token de renovación. */
class RefreshTokenServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 10, 0);

    private RefreshTokenRepository repositorio;
    private RefreshTokenService servicio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        repositorio = mock(RefreshTokenRepository.class);
        Clock reloj = Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        servicio = new RefreshTokenService(repositorio,
                new JwtProperties("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres", 15, 30), reloj);
        usuario = new Usuario();
        usuario.setId(3L);
    }

    @Test
    @DisplayName("Solo se guarda el hash del token, con vencimiento a los 30 minutos")
    void emitirGuardaHash() {
        String valor = servicio.emitir(usuario);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repositorio).save(captor.capture());
        RefreshToken guardado = captor.getValue();

        assertNotEquals(valor, guardado.getTokenHash());
        assertEquals(HashUtil.sha256(valor), guardado.getTokenHash());
        assertEquals(AHORA.plusMinutes(30), guardado.getExpiracion());
    }

    @Test
    @DisplayName("Un token vigente se acepta una sola vez (rotación)")
    void consumirRevocaElToken() {
        RefreshToken token = new RefreshToken(HashUtil.sha256("abc"), usuario, AHORA.plusMinutes(10));
        when(repositorio.findByTokenHash(HashUtil.sha256("abc"))).thenReturn(Optional.of(token));

        assertSame(usuario, servicio.consumir("abc"));
        assertTrue(token.isRevocado());

        assertThrows(CredencialesInvalidasException.class, () -> servicio.consumir("abc"));
    }

    @Test
    @DisplayName("HU-09: un token vencido por inactividad es rechazado")
    void tokenVencidoEsRechazado() {
        RefreshToken token = new RefreshToken(HashUtil.sha256("viejo"), usuario, AHORA.minusMinutes(1));
        when(repositorio.findByTokenHash(HashUtil.sha256("viejo"))).thenReturn(Optional.of(token));

        assertThrows(CredencialesInvalidasException.class, () -> servicio.consumir("viejo"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("Un token desconocido es rechazado")
    void tokenDesconocidoEsRechazado() {
        when(repositorio.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(CredencialesInvalidasException.class, () -> servicio.consumir("inventado"));
    }
}

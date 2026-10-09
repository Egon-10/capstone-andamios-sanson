package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.RestablecimientoResponse;
import com.proyecto.microservicio.dto.UsuarioActualizacionRequest;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AccesoRepository;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.PoliticaContrasena;
import com.proyecto.microservicio.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** CP-36 (desbloqueo, HU-35) y CP-37 (restablecimiento de contraseña, HU-36). */
class UsuarioServiceSprint4Test {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioRepository repositorio;
    private AuditoriaService auditoria;
    private RefreshTokenService refreshTokens;
    private UsuarioService servicio;
    private Usuario luis;

    @BeforeEach
    void preparar() {
        repositorio = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        refreshTokens = mock(RefreshTokenService.class);
        servicio = new UsuarioService(repositorio, mock(RolRepository.class), encoder, auditoria,
                mock(AccesoRepository.class), refreshTokens);
        luis = new Usuario(2L, "Luis", "luis@andamios.pe", "hash-viejo", new Rol(3L, "ENCARGADO"));
        luis.setNombreUsuario("luis.torres");
        luis.setNumeroDocumento("45678912");
        luis.setIntentosFallidos(4);
        luis.setBloqueadoHasta(ZonaHoraria.ahora().plusMinutes(10));
        when(repositorio.findById(2L)).thenReturn(Optional.of(luis));
        when(repositorio.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("CP-37: genera una temporal que cumple la política, la exige cambiar y cierra sesiones")
    void restablece() {
        when(refreshTokens.revocarTodos(2L)).thenReturn(1);

        RestablecimientoResponse r = servicio.restablecerPassword(2L, 1L);

        assertTrue(PoliticaContrasena.incumplimientos(r.passwordTemporal(), "luis.torres", "45678912").isEmpty());
        assertTrue(encoder.matches(r.passwordTemporal(), luis.getPassword()));
        assertTrue(luis.isDebeCambiarPassword());
        assertNull(luis.getBloqueadoHasta(), "el restablecimiento también desbloquea");
        assertEquals(0, luis.getIntentosFallidos());
        assertNotNull(luis.getSesionesValidasDesde());
        verify(refreshTokens).revocarTodos(2L);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoria).registrar(eq("PASSWORD_RESTABLECIDA"), detalle.capture(), eq(1L));
        assertFalse(detalle.getValue().contains(r.passwordTemporal()), "la contraseña no va a la bitácora");
    }

    @Test
    @DisplayName("CP-37: el administrador no restablece su propia contraseña por esta vía")
    void noASiMismo() {
        assertThrows(ReglaNegocioException.class, () -> servicio.restablecerPassword(2L, 2L));
    }

    @Test
    @DisplayName("CP-37: la contraseña de otro usuario no se fija a mano")
    void sinPasswordManual() {
        UsuarioActualizacionRequest conClave = new UsuarioActualizacionRequest(null, null, null, null, null, null,
                null, null, "Clave#Manual2026", "Clave#Manual2026");
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarParcial(2L, conClave, 1L));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarPerfil(2L, conClave));
        assertEquals("hash-viejo", luis.getPassword());
    }

    @Test
    @DisplayName("CP-36: el administrador desbloquea la cuenta y queda auditado")
    void desbloquea() {
        UsuarioResponse r = servicio.desbloquear(2L, 1L);

        assertFalse(r.bloqueado());
        assertNull(luis.getBloqueadoHasta());
        assertEquals(0, luis.getIntentosFallidos());
        verify(auditoria).registrar(eq("CUENTA_DESBLOQUEADA"), any(), eq(1L));
    }

    @Test
    @DisplayName("CP-36: la respuesta indica si la cuenta está bloqueada y hasta cuándo")
    void respuestaConBloqueo() {
        UsuarioResponse r = UsuarioResponse.from(luis);
        assertTrue(r.bloqueado());
        assertEquals(luis.getBloqueadoHasta(), r.bloqueadoHasta());

        luis.setBloqueadoHasta(ZonaHoraria.ahora().minusMinutes(1));
        UsuarioResponse vencido = UsuarioResponse.from(luis);
        assertFalse(vencido.bloqueado());
        assertNull(vencido.bloqueadoHasta());
    }

    @Test
    @DisplayName("Desbloquear una cuenta sin bloqueo no escribe ni audita")
    void desbloquearSinBloqueo() {
        luis.setBloqueadoHasta(null);
        luis.setIntentosFallidos(0);
        servicio.desbloquear(2L, 1L);
        verify(repositorio, never()).save(any());
        verifyNoInteractions(auditoria);
    }
}

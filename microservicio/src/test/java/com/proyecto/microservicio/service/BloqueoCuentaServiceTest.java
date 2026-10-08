package com.proyecto.microservicio.service;

import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** CP-36: bloqueo de cuenta tras intentos fallidos consecutivos (HU-35). */
class BloqueoCuentaServiceTest {

    private UsuarioRepository usuarios;
    private AuditoriaService auditoria;
    private BloqueoCuentaService servicio;
    private Usuario luis;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        servicio = new BloqueoCuentaService(usuarios, auditoria, 5, 15);
        luis = new Usuario();
        luis.setId(2L);
        luis.setNombreUsuario("luis");
        when(usuarios.findById(2L)).thenReturn(Optional.of(luis));
    }

    @Test
    @DisplayName("CP-36: cuenta los fallos sin bloquear antes del quinto")
    void cuentaFallos() {
        for (int i = 1; i <= 4; i++) {
            BloqueoCuentaService.Fallo f = servicio.registrarFallo(2L);
            assertEquals(i, f.intentos());
            assertFalse(f.bloqueo());
        }
        assertEquals(4, luis.getIntentosFallidos());
        assertNull(luis.getBloqueadoHasta());
    }

    @Test
    @DisplayName("CP-36: al quinto fallo bloquea 15 minutos, reinicia el contador y lo audita")
    void bloqueaAlQuinto() {
        luis.setIntentosFallidos(4);
        LocalDateTime antes = LocalDateTime.now().plusMinutes(14);

        BloqueoCuentaService.Fallo f = servicio.registrarFallo(2L);

        assertTrue(f.bloqueo());
        assertEquals(5, f.intentos());
        assertEquals(0, luis.getIntentosFallidos());
        assertTrue(luis.getBloqueadoHasta().isAfter(antes));
        verify(auditoria).registrar(eq("CUENTA_BLOQUEADA"), anyString(), isNull());
    }

    @Test
    @DisplayName("CP-36: un acceso correcto reinicia el contador y levanta el bloqueo vencido")
    void reinicia() {
        luis.setIntentosFallidos(3);
        luis.setBloqueadoHasta(LocalDateTime.now().minusMinutes(1));

        servicio.reiniciar(luis);

        assertEquals(0, luis.getIntentosFallidos());
        assertNull(luis.getBloqueadoHasta());
        verify(usuarios).save(luis);
    }

    @Test
    @DisplayName("Si no hay nada que reiniciar, no escribe en la base")
    void sinCambios() {
        servicio.reiniciar(luis);
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("CP-36: calcula los minutos restantes redondeando hacia arriba")
    void minutosRestantes() {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 8, 10, 0, 0);
        assertEquals(15, BloqueoCuentaService.minutosRestantes(ahora.plusMinutes(15), ahora));
        assertEquals(1, BloqueoCuentaService.minutosRestantes(ahora.plusSeconds(5), ahora));
        assertEquals(1, BloqueoCuentaService.minutosRestantes(ahora.minusSeconds(5), ahora));
        assertFalse(luis.estaBloqueado(LocalDateTime.now()));
    }
}

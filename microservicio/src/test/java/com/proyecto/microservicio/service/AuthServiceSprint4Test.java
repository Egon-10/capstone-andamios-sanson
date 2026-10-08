package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.CambioPasswordRequest;
import com.proyecto.microservicio.dto.LoginRequest;
import com.proyecto.microservicio.dto.LoginResponse;
import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import com.proyecto.microservicio.exception.CuentaBloqueadaException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.TokenRevocadoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.JwtProperties;
import com.proyecto.microservicio.security.JwtService;
import com.proyecto.microservicio.security.OrigenSolicitud;
import com.proyecto.microservicio.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/** CP-36 (bloqueo, HU-35) y CP-38 (cambio de contraseña, HU-37). */
class AuthServiceSprint4Test {

    private static final String ACTUAL = "Clave#Actual2026";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioRepository usuarios;
    private RefreshTokenService refreshTokens;
    private AuditoriaService auditoria;
    private AccesoService accesos;
    private BloqueoCuentaService bloqueo;
    private JwtService jwt;
    private AuthService servicio;
    private Usuario luis;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        refreshTokens = mock(RefreshTokenService.class);
        auditoria = mock(AuditoriaService.class);
        accesos = mock(AccesoService.class);
        bloqueo = mock(BloqueoCuentaService.class);
        jwt = mock(JwtService.class);
        servicio = new AuthService(usuarios, encoder, jwt, refreshTokens, mock(TokenRevocadoRepository.class),
                auditoria, new JwtProperties("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres", 15, 30),
                accesos, bloqueo);

        luis = new Usuario(2L, "Luis", "luis@andamios.pe", encoder.encode(ACTUAL), new Rol(3L, "ENCARGADO"));
        luis.setNombreUsuario("luis.torres");
        luis.setEstado(Usuario.ESTADO_ACTIVO);
        when(usuarios.findByNombreUsuario("luis.torres")).thenReturn(Optional.of(luis));
        when(usuarios.findById(2L)).thenReturn(Optional.of(luis));
        when(jwt.generarTokenAcceso(any())).thenReturn("acceso-nuevo");
        when(refreshTokens.emitir(any())).thenReturn("renovacion-nueva");
    }

    // --- HU-35 ---

    @Test
    @DisplayName("CP-36: con la cuenta bloqueada responde 423 sin comprobar la contraseña")
    void cuentaBloqueada() {
        luis.setBloqueadoHasta(ZonaHoraria.ahora().plusMinutes(10));
        LoginRequest correcta = new LoginRequest("luis.torres", ACTUAL);

        CuentaBloqueadaException ex = assertThrows(CuentaBloqueadaException.class, () -> servicio.login(correcta));

        assertTrue(ex.getMinutosRestantes() >= 9 && ex.getMinutosRestantes() <= 10);
        verify(accesos).registrar(eq("luis.torres"), eq(luis), eq(Acceso.BLOQUEADO), anyString(), any());
        verify(bloqueo, never()).registrarFallo(any());
        verify(refreshTokens, never()).emitir(any());
    }

    @Test
    @DisplayName("CP-36: el fallo que alcanza el máximo bloquea y lo informa")
    void falloQueBloquea() {
        when(bloqueo.registrarFallo(2L)).thenReturn(
                new BloqueoCuentaService.Fallo(5, 5, ZonaHoraria.ahora().plusMinutes(15)));
        LoginRequest incorrecta = new LoginRequest("luis.torres", "otra");

        assertThrows(CuentaBloqueadaException.class, () -> servicio.login(incorrecta));
        verify(accesos).registrar(eq("luis.torres"), eq(luis), eq(Acceso.BLOQUEADO),
                startsWith("Contraseña incorrecta; cuenta bloqueada"), any());
    }

    @Test
    @DisplayName("CP-36: un fallo antes del máximo responde el mensaje genérico")
    void falloSinBloqueo() {
        when(bloqueo.registrarFallo(2L)).thenReturn(new BloqueoCuentaService.Fallo(2, 5, null));
        LoginRequest incorrecta = new LoginRequest("luis.torres", "otra");

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.login(incorrecta));
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    @DisplayName("CP-36: un bloqueo vencido ya no impide ingresar y el acceso reinicia el contador")
    void bloqueoVencido() {
        luis.setBloqueadoHasta(ZonaHoraria.ahora().minusMinutes(1));

        LoginResponse r = servicio.login(new LoginRequest("luis.torres", ACTUAL), OrigenSolicitud.DESCONOCIDO);

        assertEquals("acceso-nuevo", r.accessToken());
        verify(bloqueo).reiniciar(luis);
    }

    // --- HU-37 ---

    @Test
    @DisplayName("CP-38: cambia la contraseña, cierra las demás sesiones y abre una nueva")
    void cambiaPassword() {
        luis.setDebeCambiarPassword(true);
        when(refreshTokens.revocarTodos(2L)).thenReturn(3);

        LoginResponse r = servicio.cambiarPassword(2L, new CambioPasswordRequest(ACTUAL, "Nueva#Clave2026", "Nueva#Clave2026"));

        assertTrue(encoder.matches("Nueva#Clave2026", luis.getPassword()));
        assertFalse(luis.isDebeCambiarPassword());
        assertNotNull(luis.getFechaCambioPassword());
        assertEquals(luis.getFechaCambioPassword(), luis.getSesionesValidasDesde());
        verify(refreshTokens).revocarTodos(2L);
        verify(auditoria).registrar(eq("PASSWORD_CAMBIADA"), contains("3 sesiones"), eq(2L));
        assertEquals("acceso-nuevo", r.accessToken());
        assertEquals("renovacion-nueva", r.refreshToken());
    }

    @Test
    @DisplayName("CP-38: exige la contraseña actual correcta")
    void exigeActual() {
        CambioPasswordRequest s = new CambioPasswordRequest("equivocada", "Nueva#Clave2026", "Nueva#Clave2026");
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> servicio.cambiarPassword(2L, s));
        assertEquals("actual", ex.getCampo());
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("CP-38: rechaza confirmación distinta, repetir la actual y una clave débil")
    void rechazaNuevaInvalida() {
        CambioPasswordRequest distinta = new CambioPasswordRequest(ACTUAL, "Nueva#Clave2026", "Nueva#Clave2027");
        CambioPasswordRequest repetida = new CambioPasswordRequest(ACTUAL, ACTUAL, ACTUAL);
        CambioPasswordRequest debil = new CambioPasswordRequest(ACTUAL, "debil", "debil");

        assertEquals("confirmacion", assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarPassword(2L, distinta)).getCampo());
        assertEquals("nueva", assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarPassword(2L, repetida)).getCampo());
        assertEquals("nueva", assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarPassword(2L, debil)).getCampo());
        verify(refreshTokens, never()).revocarTodos(any());
    }
}

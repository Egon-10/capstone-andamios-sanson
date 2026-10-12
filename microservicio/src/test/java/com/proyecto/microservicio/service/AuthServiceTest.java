package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.LoginRequest;
import com.proyecto.microservicio.dto.LoginResponse;
import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.TokenRevocado;
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
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** CP-03 (inicio de sesión) y CP-08 (cierre de sesión). */
class AuthServiceTest {

    private UsuarioRepository usuarios;
    private PasswordEncoder encoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokens;
    private TokenRevocadoRepository revocados;
    private AuditoriaService auditoria;
    private AccesoService accesos;
    private BloqueoCuentaService bloqueo;
    private AuthService servicio;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hash-senuelo");
        jwtService = mock(JwtService.class);
        refreshTokens = mock(RefreshTokenService.class);
        revocados = mock(TokenRevocadoRepository.class);
        auditoria = mock(AuditoriaService.class);
        accesos = mock(AccesoService.class);
        bloqueo = mock(BloqueoCuentaService.class);
        when(bloqueo.registrarFallo(any())).thenReturn(new BloqueoCuentaService.Fallo(1, 5, null));
        servicio = new AuthService(usuarios, encoder, jwtService, refreshTokens, revocados, auditoria,
                new JwtProperties("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres", 15, 30), accesos, bloqueo);

        ana = new Usuario(5L, "Ana", "ana@andamios.pe", "$2a$10$hash", new Rol(1L, "ADMINISTRADOR"));
        ana.setNombreUsuario("ana.perez");
        ana.setEstado(Usuario.ESTADO_ACTIVO);

        when(jwtService.generarTokenAcceso(ana)).thenReturn("token-acceso");
        when(jwtService.segundosDeVigencia()).thenReturn(900L);
        when(refreshTokens.emitir(ana)).thenReturn("token-renovacion");
    }

    @Test
    @DisplayName("CP-03: con credenciales válidas emite el token de acceso y el de renovación")
    void loginExitoso() {
        when(usuarios.findByCorreoIgnoreCase("ana@andamios.pe")).thenReturn(Optional.of(ana));
        when(encoder.matches("Clave2026", "$2a$10$hash")).thenReturn(true);

        LoginResponse r = servicio.login(new LoginRequest("ana@andamios.pe", "Clave2026"));

        assertEquals("token-acceso", r.accessToken());
        assertEquals("token-renovacion", r.refreshToken());
        assertEquals("Bearer", r.tipo());
        assertEquals(1800L, r.inactividadMaximaSegundos());
        assertEquals("ana@andamios.pe", r.usuario().correo());
        verify(auditoria).registrar("INICIO DE SESIÓN", 5L);
        verify(accesos).registrar("ana@andamios.pe", ana, Acceso.EXITOSO, null, OrigenSolicitud.DESCONOCIDO);
    }

    @Test
    @DisplayName("Permite iniciar sesión con el nombre de usuario")
    void loginConNombreDeUsuario() {
        when(usuarios.findByNombreUsuario("ana.perez")).thenReturn(Optional.of(ana));
        when(encoder.matches("Clave2026", "$2a$10$hash")).thenReturn(true);

        assertEquals("token-acceso", servicio.login(new LoginRequest("ana.perez", "Clave2026")).accessToken());
    }

    @Test
    @DisplayName("CP-03: con contraseña incorrecta responde un mensaje genérico y no emite tokens")
    void contrasenaIncorrecta() {
        when(usuarios.findByCorreoIgnoreCase("ana@andamios.pe")).thenReturn(Optional.of(ana));
        when(encoder.matches("otra", "$2a$10$hash")).thenReturn(false);

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.login(new LoginRequest("ana@andamios.pe", "otra")));

        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(refreshTokens, never()).emitir(any());
        // CP-33: el fallo queda en la bitácora con su causa real.
        verify(accesos).registrar("ana@andamios.pe", ana, Acceso.FALLIDO, "Contraseña incorrecta (intento 1 de 5)",
                OrigenSolicitud.DESCONOCIDO);
    }

    @Test
    @DisplayName("CP-03: un usuario inexistente recibe el mismo mensaje que una contraseña incorrecta")
    void usuarioInexistente() {
        when(usuarios.findByCorreoIgnoreCase("nadie@andamios.pe")).thenReturn(Optional.empty());

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.login(new LoginRequest("nadie@andamios.pe", "x")));

        assertEquals("Credenciales inválidas", ex.getMessage());
        // Se compara contra un hash señuelo para que el tiempo de respuesta no
        // revele que la cuenta no existe.
        verify(encoder).matches("x", "hash-senuelo");
        verify(accesos).registrar("nadie@andamios.pe", null, Acceso.FALLIDO, "Cuenta inexistente",
                OrigenSolicitud.DESCONOCIDO);
    }

    @Test
    @DisplayName("Una cuenta inactiva no puede iniciar sesión")
    void cuentaInactiva() {
        ana.setEstado(Usuario.ESTADO_INACTIVO);
        when(usuarios.findByCorreoIgnoreCase("ana@andamios.pe")).thenReturn(Optional.of(ana));

        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.login(new LoginRequest("ana@andamios.pe", "Clave2026")));
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-09: la renovación emite tokens nuevos para el usuario del token consumido")
    void renovarSesion() {
        when(refreshTokens.consumir("token-renovacion")).thenReturn(ana);

        LoginResponse r = servicio.renovar("token-renovacion");

        assertEquals("token-acceso", r.accessToken());
        verify(refreshTokens).emitir(ana);
    }

    @Test
    @DisplayName("CP-08: el cierre de sesión revoca la renovación e invalida el token de acceso actual")
    void logoutInvalidaTokens() {
        Instant vence = Instant.now().plusSeconds(600);

        servicio.logout("token-renovacion", "jti-123", vence, 5L);

        verify(refreshTokens).revocar("token-renovacion");
        ArgumentCaptor<TokenRevocado> captor = ArgumentCaptor.forClass(TokenRevocado.class);
        verify(revocados).save(captor.capture());
        assertEquals("jti-123", captor.getValue().getJti());
        assertEquals(vence, captor.getValue().getExpiracion());
        verify(auditoria).registrar("CIERRE DE SESIÓN", 5L);
    }
}

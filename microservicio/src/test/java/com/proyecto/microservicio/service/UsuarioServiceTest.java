package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.UsuarioActualizacionRequest;
import com.proyecto.microservicio.dto.UsuarioRegistroRequest;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AccesoRepository;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.security.RefreshTokenService;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CP-04 (cifrado), CP-07 (edición parcial) y CP-11 (registro de usuario, HU-43). */
class UsuarioServiceTest {

    private UsuarioRepository repositorio;
    private RolRepository roles;
    private AuditoriaService auditoria;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private UsuarioService servicio;

    private AccesoRepository accesos;
    private RefreshTokenService refreshTokens;

    @BeforeEach
    void preparar() {
        repositorio = mock(UsuarioRepository.class);
        roles = mock(RolRepository.class);
        auditoria = mock(AuditoriaService.class);
        accesos = mock(AccesoRepository.class);
        refreshTokens = mock(RefreshTokenService.class);
        servicio = new UsuarioService(repositorio, roles, encoder, auditoria, accesos, refreshTokens);
        when(repositorio.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(10L);
            }
            return u;
        });
    }

    private static UsuarioRegistroRequest solicitud(String tipoDoc, String documento, String password, String confirmacion) {
        return new UsuarioRegistroRequest("Ana María", "Pérez Soto", tipoDoc, documento, "Ana@Andamios.pe",
                "987654321", "ana.perez", password, confirmacion, 2L, "LOGISTICA", "MANANA");
    }

    private static UsuarioRegistroRequest valida() {
        return solicitud("DNI", "45678912", "Clave2026", "Clave2026");
    }

    @Test
    @DisplayName("CP-11 y CP-04: registra el usuario con la contraseña cifrada, activo y auditado")
    void registroValido() {
        when(roles.findById(2L)).thenReturn(Optional.of(new Rol(2L, "ENCARGADO")));

        UsuarioResponse r = servicio.registrar(valida(), 1L);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).save(captor.capture());
        Usuario guardado = captor.getValue();

        assertNotEquals("Clave2026", guardado.getPassword());
        assertTrue(encoder.matches("Clave2026", guardado.getPassword()));
        assertEquals("ana@andamios.pe", guardado.getCorreo());
        assertEquals(Usuario.ESTADO_ACTIVO, r.estado());
        assertEquals("ENCARGADO", r.rol().nombre());
        assertNotNull(guardado.getFechaCreacion());
        verify(auditoria).registrar("CREAR USUARIO: ana.perez", 1L);
    }

    @Test
    @DisplayName("CP-11: rechaza un correo ya registrado e indica el campo")
    void correoDuplicado() {
        when(repositorio.existsByCorreoIgnoreCase("ana@andamios.pe")).thenReturn(true);

        ConflictoException ex = assertThrows(ConflictoException.class, () -> servicio.registrar(valida(), 1L));

        assertEquals("correo", ex.getCampo());
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("CP-11: rechaza un nombre de usuario ya registrado")
    void nombreUsuarioDuplicado() {
        when(repositorio.existsByNombreUsuario("ana.perez")).thenReturn(true);

        ConflictoException ex = assertThrows(ConflictoException.class, () -> servicio.registrar(valida(), 1L));

        assertEquals("nombreUsuario", ex.getCampo());
    }

    @Test
    @DisplayName("CP-11: rechaza un número de documento ya registrado")
    void documentoDuplicado() {
        when(repositorio.existsByNumeroDocumento("45678912")).thenReturn(true);

        ConflictoException ex = assertThrows(ConflictoException.class, () -> servicio.registrar(valida(), 1L));

        assertEquals("numeroDocumento", ex.getCampo());
    }

    @Test
    @DisplayName("CP-11: la contraseña y su confirmación deben coincidir")
    void confirmacionDistinta() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(solicitud("DNI", "45678912", "Clave2026", "Clave2027"), 1L));

        assertEquals("confirmarPassword", ex.getCampo());
    }

    @Test
    @DisplayName("CP-11: un DNI debe tener exactamente 8 dígitos")
    void dniInvalido() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(solicitud("DNI", "4567891", "Clave2026", "Clave2026"), 1L));

        assertEquals("numeroDocumento", ex.getCampo());
    }

    @Test
    @DisplayName("CP-07: la edición parcial solo cambia los campos enviados")
    void edicionParcial() {
        Usuario existente = new Usuario(4L, "Luis", "luis@andamios.pe", "hash-original", new Rol(3L, "GERENTE"));
        existente.setApellidos("Quispe");
        when(repositorio.findById(4L)).thenReturn(Optional.of(existente));

        UsuarioActualizacionRequest soloTelefono =
                new UsuarioActualizacionRequest(null, null, null, "912345678", null, null, null, null, null, null);
        UsuarioResponse r = servicio.actualizarParcial(4L, soloTelefono, 1L);

        assertEquals("912345678", r.telefono());
        assertEquals("Luis", r.nombre());
        assertEquals("Quispe", r.apellidos());
        assertEquals("luis@andamios.pe", r.correo());
        assertEquals("GERENTE", r.rol().nombre());
        assertEquals("hash-original", existente.getPassword());
    }

    @Test
    @DisplayName("Un administrador no puede desactivar su propia cuenta")
    void noDesactivaSuPropiaCuenta() {
        Usuario admin = new Usuario(1L, "Admin", "admin@andamios.pe", "hash", new Rol(1L, "ADMINISTRADOR"));
        when(repositorio.findById(1L)).thenReturn(Optional.of(admin));

        UsuarioActualizacionRequest desactivar =
                new UsuarioActualizacionRequest(null, null, null, null, null, null, null, "INACTIVO", null, null);

        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarParcial(1L, desactivar, 1L));
    }

    @Test
    @DisplayName("Un administrador no puede eliminar su propia cuenta")
    void noEliminaSuPropiaCuenta() {
        assertThrows(ReglaNegocioException.class, () -> servicio.eliminar(1L, 1L));
        verify(repositorio, never()).delete(any());
    }
}

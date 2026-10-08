package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.CambioEstadoRequest;
import com.proyecto.microservicio.dto.PerfilResponse;
import com.proyecto.microservicio.dto.UsuarioActualizacionRequest;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AccesoRepository;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * CP-31 (listado y búsqueda, HU-30), CP-32 (activación y desactivación,
 * HU-31) y CP-35 (perfil propio, HU-34).
 */
class UsuarioServiceSprint3Test {

    private UsuarioRepository repositorio;
    private AuditoriaService auditoria;
    private AccesoRepository accesos;
    private RefreshTokenService refreshTokens;
    private UsuarioService servicio;

    private Usuario admin;
    private Usuario encargado;

    @BeforeEach
    void preparar() {
        repositorio = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        accesos = mock(AccesoRepository.class);
        refreshTokens = mock(RefreshTokenService.class);
        servicio = new UsuarioService(repositorio, mock(RolRepository.class), mock(PasswordEncoder.class),
                auditoria, accesos, refreshTokens);

        admin = new Usuario(1L, "Ana", "ana@andamios.pe", "x", new Rol(1L, "ADMINISTRADOR"));
        admin.setNombreUsuario("ana");
        admin.setEstado(Usuario.ESTADO_ACTIVO);
        encargado = new Usuario(2L, "Luis", "luis@andamios.pe", "x", new Rol(3L, "ENCARGADO"));
        encargado.setNombreUsuario("luis");
        encargado.setEstado(Usuario.ESTADO_ACTIVO);

        when(repositorio.findById(1L)).thenReturn(Optional.of(admin));
        when(repositorio.findById(2L)).thenReturn(Optional.of(encargado));
        when(repositorio.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    // --- HU-30 ---

    @Test
    @DisplayName("CP-31: busca con texto escapado, rol, estado y orden permitido")
    void buscaConFiltros() {
        Page<Usuario> pagina = new PageImpl<>(List.of(encargado));
        when(repositorio.buscar(anyString(), eq(3L), eq("ACTIVO"), any(Pageable.class))).thenReturn(pagina);

        Page<Usuario> r = servicio.buscar(" Lu_is ", 3L, "activo", 0, 500, "apellidos", true);

        assertEquals(1, r.getTotalElements());
        ArgumentCaptor<Pageable> paginaPedida = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).buscar(eq("%lu!_is%"), eq(3L), eq("ACTIVO"), paginaPedida.capture());
        assertEquals(100, paginaPedida.getValue().getPageSize(), "el tamaño se acota a 100");
        assertNotNull(paginaPedida.getValue().getSort().getOrderFor("apellidos"));
        assertTrue(paginaPedida.getValue().getSort().getOrderFor("apellidos").isDescending());
    }

    @Test
    @DisplayName("CP-31: un campo de orden desconocido vuelve al orden por nombre")
    void ordenDesconocido() {
        when(repositorio.buscar(isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        servicio.buscar(null, null, null, -3, 10, "password", false);

        ArgumentCaptor<Pageable> paginaPedida = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).buscar(isNull(), isNull(), isNull(), paginaPedida.capture());
        assertEquals(0, paginaPedida.getValue().getPageNumber());
        assertNotNull(paginaPedida.getValue().getSort().getOrderFor("nombre"));
        assertNull(paginaPedida.getValue().getSort().getOrderFor("password"));
    }

    @Test
    @DisplayName("CP-31: rechaza un estado que no existe")
    void estadoInvalido() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.buscar(null, null, "SUSPENDIDO", 0, 10, null, false));
    }

    // --- HU-31 ---

    @Test
    @DisplayName("CP-32: desactivar cierra las sesiones abiertas y deja constancia del motivo")
    void desactivarCierraSesiones() {
        when(refreshTokens.revocarTodos(2L)).thenReturn(2);

        UsuarioResponse r = servicio.cambiarEstado(2L, new CambioEstadoRequest("INACTIVO", "Dejó la empresa"), 1L);

        assertEquals(Usuario.ESTADO_INACTIVO, r.estado());
        assertNotNull(encargado.getSesionesValidasDesde());
        assertEquals(0, encargado.getSesionesValidasDesde().getNano(), "se trunca a segundos como el iat del token");
        verify(refreshTokens).revocarTodos(2L);
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoria).registrar(eq("USUARIO_DESACTIVADO"), detalle.capture(), eq(1L));
        assertTrue(detalle.getValue().contains("2 sesiones"));
        assertTrue(detalle.getValue().contains("Dejó la empresa"));
    }

    @Test
    @DisplayName("CP-32: nadie puede desactivar su propia cuenta")
    void noSePuedeDesactivarASiMismo() {
        CambioEstadoRequest s = new CambioEstadoRequest("INACTIVO", null);
        assertThrows(ReglaNegocioException.class, () -> servicio.cambiarEstado(1L, s, 1L));
        verify(refreshTokens, never()).revocarTodos(any());
    }

    @Test
    @DisplayName("CP-32: no se puede desactivar al único administrador activo")
    void protegeAlUltimoAdministrador() {
        when(repositorio.contarActivosConRol("ADMINISTRADOR")).thenReturn(1L);
        CambioEstadoRequest s = new CambioEstadoRequest("INACTIVO", null);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarEstado(1L, s, 99L));
        assertTrue(ex.getMessage().contains("único administrador"));
    }

    @Test
    @DisplayName("CP-32: con otro administrador activo, sí se puede desactivar")
    void conOtroAdministrador() {
        when(repositorio.contarActivosConRol("ADMINISTRADOR")).thenReturn(2L);

        UsuarioResponse r = servicio.cambiarEstado(1L, new CambioEstadoRequest("INACTIVO", null), 99L);

        assertEquals(Usuario.ESTADO_INACTIVO, r.estado());
    }

    @Test
    @DisplayName("CP-32: reactivar devuelve el acceso y queda en la bitácora")
    void reactivar() {
        encargado.setEstado(Usuario.ESTADO_INACTIVO);

        UsuarioResponse r = servicio.cambiarEstado(2L, new CambioEstadoRequest("ACTIVO", null), 1L);

        assertEquals(Usuario.ESTADO_ACTIVO, r.estado());
        verify(auditoria).registrar(eq("USUARIO_ACTIVADO"), anyString(), eq(1L));
        verify(refreshTokens, never()).revocarTodos(any());
    }

    @Test
    @DisplayName("CP-32: pedir el estado que ya tiene no hace nada")
    void sinCambio() {
        servicio.cambiarEstado(2L, new CambioEstadoRequest("ACTIVO", null), 1L);

        verify(repositorio, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("CP-32: no se puede quitar el rol de administrador al último administrador")
    void noQuitarRolAlUltimoAdministrador() {
        RolRepository roles = mock(RolRepository.class);
        when(roles.findById(3L)).thenReturn(Optional.of(new Rol(3L, "ENCARGADO")));
        UsuarioService s = new UsuarioService(repositorio, roles, mock(PasswordEncoder.class), auditoria,
                accesos, refreshTokens);
        when(repositorio.contarActivosConRol("ADMINISTRADOR")).thenReturn(1L);
        UsuarioActualizacionRequest cambio = new UsuarioActualizacionRequest(null, null, null, null, null, null,
                3L, null, null, null);

        assertThrows(ReglaNegocioException.class, () -> s.actualizarParcial(1L, cambio, 99L));
    }

    // --- HU-34 ---

    @Test
    @DisplayName("CP-35: el perfil muestra el acceso anterior y los fallidos desde entonces")
    void perfilConAccesoAnterior() {
        Acceso actual = acceso(LocalDateTime.of(2026, 10, 8, 9, 0));
        Acceso anterior = acceso(LocalDateTime.of(2026, 10, 7, 18, 0));
        when(accesos.findTop2ByUsuario_IdAndResultadoOrderByFechaDescIdDesc(2L, Acceso.EXITOSO))
                .thenReturn(List.of(actual, anterior));
        when(accesos.countByUsuario_IdAndResultadoAndFechaAfter(2L, Acceso.FALLIDO, anterior.getFecha()))
                .thenReturn(3L);

        PerfilResponse p = servicio.perfil(2L);

        assertEquals("luis", p.usuario().nombreUsuario());
        assertEquals(anterior.getFecha(), p.accesoAnterior());
        assertEquals(3, p.fallidosDesdeAnterior());
    }

    @Test
    @DisplayName("CP-35: en el primer acceso no hay acceso anterior")
    void primerAcceso() {
        when(accesos.findTop2ByUsuario_IdAndResultadoOrderByFechaDescIdDesc(2L, Acceso.EXITOSO))
                .thenReturn(List.of(acceso(LocalDateTime.now())));

        PerfilResponse p = servicio.perfil(2L);

        assertNull(p.accesoAnterior());
        assertEquals(0, p.fallidosDesdeAnterior());
    }

    @Test
    @DisplayName("CP-35: el usuario no puede cambiarse el área, el turno, el rol ni el estado")
    void perfilNoCambiaDatosDeOrganizacion() {
        UsuarioActualizacionRequest area = new UsuarioActualizacionRequest(null, null, null, null, "SISTEMAS",
                null, null, null, null, null);
        UsuarioActualizacionRequest rol = new UsuarioActualizacionRequest(null, null, null, null, null, null,
                1L, null, null, null);

        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarPerfil(2L, area));
        assertThrows(ReglaNegocioException.class, () -> servicio.actualizarPerfil(2L, rol));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("CP-35: el usuario sí actualiza sus datos de contacto")
    void perfilActualizaContacto() {
        UsuarioActualizacionRequest contacto = new UsuarioActualizacionRequest(null, null, null, "999888777",
                null, null, null, null, null, null);

        UsuarioResponse r = servicio.actualizarPerfil(2L, contacto);

        assertEquals("999888777", r.telefono());
        verify(auditoria).registrar("EDITAR PERFIL PROPIO", 2L);
    }

    private static Acceso acceso(LocalDateTime fecha) {
        Acceso a = new Acceso();
        a.setFecha(fecha);
        a.setResultado(Acceso.EXITOSO);
        return a;
    }
}

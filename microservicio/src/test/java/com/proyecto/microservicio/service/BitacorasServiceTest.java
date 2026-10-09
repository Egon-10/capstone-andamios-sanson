package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.AccesoResponse;
import com.proyecto.microservicio.dto.AuditoriaResponse;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AccesoRepository;
import com.proyecto.microservicio.repository.AuditoriaRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.OrigenSolicitud;
import com.proyecto.microservicio.support.Tokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * CP-33 (bitácora de accesos, HU-32) y CP-34 (consulta de auditoría con
 * filtros, HU-33).
 */
class BitacorasServiceTest {

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // --- Filtro comun ---

    @Test
    @DisplayName("CP-34: el texto se busca en minúsculas, en cualquier parte y con los comodines escapados")
    void patronDeTexto() {
        assertEquals("%anulado%", new FiltroBitacora(" ANULADO ", null, null, null, null).patronTexto());
        assertEquals("%50!%!_x!!%", new FiltroBitacora("50%_x!", null, null, null, null).patronTexto());
        assertNull(new FiltroBitacora("  ", null, null, null, null).patronTexto());
    }

    @Test
    @DisplayName("CP-34: la fecha final incluye el día completo")
    void rangoDeFechas() {
        FiltroBitacora f = new FiltroBitacora(null, null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8));
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), f.inicio());
        assertEquals(LocalDateTime.of(2026, 10, 9, 0, 0), f.fin());
    }

    @Test
    @DisplayName("CP-34: rechaza un rango con la fecha inicial posterior a la final")
    void rangoInvertido() {
        LocalDate hoy = LocalDate.of(2026, 10, 8);
        LocalDate ayer = hoy.minusDays(1);
        assertThrows(ReglaNegocioException.class, () -> new FiltroBitacora(null, null, null, hoy, ayer));
    }

    // --- Auditoria ---

    @Test
    @DisplayName("CP-34: consulta paginada con todos los filtros, lo más reciente primero")
    void consultaAuditoria() {
        AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
        when(repositorio.buscar(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        AuditoriaService servicio = new AuditoriaService(repositorio, mock(UsuarioRepository.class));

        servicio.buscar(new FiltroBitacora("anul", 3L, null, LocalDate.of(2026, 10, 1), null), 2, 1000);

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).buscar(eq("%anul%"), eq(3L), eq(LocalDateTime.of(2026, 10, 1, 0, 0)), isNull(),
                pagina.capture());
        assertEquals(2, pagina.getValue().getPageNumber());
        assertEquals(AuditoriaService.TAMANO_MAXIMO_PAGINA, pagina.getValue().getPageSize());
        assertTrue(pagina.getValue().getSort().getOrderFor("fecha").isDescending());
    }

    @Test
    @DisplayName("El catálogo audita a nombre del usuario de la solicitud, tomado del token")
    void registraComoUsuarioActual() {
        AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario ana = new Usuario();
        ana.setId(7L);
        when(usuarios.findById(7L)).thenReturn(Optional.of(ana));
        when(repositorio.save(any(Auditoria.class))).thenAnswer(i -> i.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Tokens.deUsuario(7L)));

        Auditoria a = new AuditoriaService(repositorio, usuarios).registrarComoUsuarioActual("PRODUCTO_CREADO", "X");

        assertSame(ana, a.getUsuario());
        assertEquals("PRODUCTO_CREADO", a.getAccion());
    }

    @Test
    @DisplayName("Sin sesión, la acción queda registrada a nombre del sistema")
    void registraSinSesion() {
        AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
        when(repositorio.save(any(Auditoria.class))).thenAnswer(i -> i.getArgument(0));

        Auditoria a = new AuditoriaService(repositorio, mock(UsuarioRepository.class))
                .registrarComoUsuarioActual("TAREA", null);

        assertNull(a.getUsuario());
    }

    @Test
    @DisplayName("CP-34: la respuesta de auditoría no expone documento ni contacto del usuario")
    void respuestaSinDatosPersonales() {
        Usuario u = new Usuario();
        u.setId(3L);
        u.setNombre("Luis");
        u.setApellidos("Rojas");
        u.setNombreUsuario("luis");
        u.setNumeroDocumento("12345678");
        Auditoria a = new Auditoria(1L, "MOVIMIENTO_ANULADO", LocalDateTime.now(), u);

        AuditoriaResponse r = AuditoriaResponse.de(a);

        assertEquals("Luis Rojas", r.usuario());
        assertFalse(r.toString().contains("12345678"));
        assertEquals("Sistema", AuditoriaResponse.de(new Auditoria(2L, "X", LocalDateTime.now(), null)).usuario());
    }

    // --- Accesos ---

    @Test
    @DisplayName("CP-33: registra el intento con IP, navegador y el identificador normalizado")
    void registraAcceso() {
        AccesoRepository repositorio = mock(AccesoRepository.class);
        when(repositorio.save(any(Acceso.class))).thenAnswer(i -> i.getArgument(0));
        AccesoService servicio = new AccesoService(repositorio);

        Acceso a = servicio.registrar("  Ana.Perez ", null, Acceso.FALLIDO, "Cuenta inexistente",
                new OrigenSolicitud("10.0.0.5", "Firefox"));

        assertEquals("ana.perez", a.getIdentificador());
        assertEquals(Acceso.FALLIDO, a.getResultado());
        assertEquals("10.0.0.5", a.getIp());
        assertEquals("Firefox", a.getAgente());
        assertNotNull(a.getFecha());
    }

    @Test
    @DisplayName("CP-33: un identificador vacío o muy largo no impide registrar el intento")
    void identificadorExtremo() {
        assertEquals("(vacío)", AccesoService.identificador("  "));
        assertEquals(100, AccesoService.identificador("x".repeat(300)).length());
    }

    @Test
    @DisplayName("CP-33: un resultado desconocido no filtra")
    void resultadoDesconocido() {
        assertEquals("FALLIDO", AccesoService.resultado(" fallido "));
        assertNull(AccesoService.resultado("CUALQUIERA"));
        assertNull(AccesoService.resultado(null));
    }

    @Test
    @DisplayName("CP-33: la respuesta del acceso muestra el usuario cuando la cuenta existe")
    void respuestaDeAcceso() {
        Usuario u = new Usuario();
        u.setId(4L);
        u.setNombre("Rosa");
        Acceso a = new Acceso();
        a.setUsuario(u);
        a.setResultado(Acceso.EXITOSO);

        AccesoResponse r = AccesoResponse.de(a);

        assertEquals(4L, r.usuarioId());
        assertEquals("Rosa", r.usuario());
        assertNull(AccesoResponse.de(new Acceso()).usuario());
    }
}

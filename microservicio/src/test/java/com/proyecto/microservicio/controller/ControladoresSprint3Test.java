package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.CambioEstadoRequest;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.dto.PaginaResponse;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.reporte.Reporte;
import com.proyecto.microservicio.service.AccesoService;
import com.proyecto.microservicio.service.AuditoriaService;
import com.proyecto.microservicio.service.ReporteService;
import com.proyecto.microservicio.service.UsuarioService;
import com.proyecto.microservicio.support.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Controladores del Sprint 3: reportes, bitácoras y usuarios. */
class ControladoresSprint3Test {

    private static final Jwt ANA = Tokens.deUsuario(1L);

    private static Reporte reporte() {
        return new Reporte("Prueba", List.of("Filtro"), List.of(Reporte.texto("A", 5)),
                List.of(List.<Object>of("x")), null, null, "Ana", LocalDateTime.of(2026, 10, 8, 10, 0));
    }

    @Test
    @DisplayName("CP-28: exporta en Excel como adjunto, sin caché y lo registra en la auditoría")
    void exportaExcel() {
        ReporteService reportes = mock(ReporteService.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(reportes.movimientos(null, null, null, 1L)).thenReturn(reporte());
        ReporteController c = new ReporteController(reportes, auditoria);

        ResponseEntity<byte[]> r = c.movimientos(null, null, null, "xlsx", ANA);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                r.getHeaders().getContentType().toString());
        String adjunto = r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(adjunto.startsWith("attachment"));
        assertTrue(adjunto.contains("reporte-movimientos-"));
        assertTrue(adjunto.contains(".xlsx"));
        assertEquals("no-store", r.getHeaders().getCacheControl());
        assertEquals('P', (char) r.getBody()[0], "un xlsx es un ZIP y empieza con PK");
        verify(auditoria).registrar(eq("REPORTE_EXPORTADO"), anyString(), eq(1L));
    }

    @Test
    @DisplayName("CP-27: el formato por omisión es PDF")
    void exportaPdfPorOmision() {
        ReporteService reportes = mock(ReporteService.class);
        when(reportes.stockCritico(1L)).thenReturn(reporte());
        ReporteController c = new ReporteController(reportes, mock(AuditoriaService.class));

        ResponseEntity<byte[]> r = c.stockCritico(null, ANA);

        assertEquals("application/pdf", r.getHeaders().getContentType().toString());
        assertEquals("%PDF", new String(r.getBody(), 0, 4));
    }

    @Test
    @DisplayName("Cada ruta de reporte pasa sus filtros al servicio")
    void rutasDeReporte() {
        ReporteService reportes = mock(ReporteService.class);
        when(reportes.productos(any(), any())).thenReturn(reporte());
        when(reportes.valorizacion(any(), any())).thenReturn(reporte());
        when(reportes.kardex(any(), any(), any(), any())).thenReturn(reporte());
        when(reportes.auditoria(any(), any())).thenReturn(reporte());
        when(reportes.accesos(any(), any())).thenReturn(reporte());
        when(reportes.usuarios(any(), any(), any(), any())).thenReturn(reporte());
        ReporteController c = new ReporteController(reportes, mock(AuditoriaService.class));
        LocalDate dia = LocalDate.of(2026, 9, 30);

        c.productos(4L, "pdf", ANA);
        c.valorizacion(dia, "xlsx", ANA);
        c.kardex(5L, dia, dia, "pdf", ANA);
        c.auditoria("anul", 2L, dia, dia, "pdf", ANA);
        c.accesos(null, null, "FALLIDO", null, null, "xlsx", ANA);
        c.usuarios("luis", 3L, "ACTIVO", "pdf", ANA);

        verify(reportes).productos(4L, 1L);
        verify(reportes).valorizacion(dia, 1L);
        verify(reportes).kardex(5L, dia, dia, 1L);
        verify(reportes).auditoria(new FiltroBitacora("anul", 2L, null, dia, dia), 1L);
        verify(reportes).accesos(new FiltroBitacora(null, null, "FALLIDO", null, null), 1L);
        verify(reportes).usuarios("luis", 3L, "ACTIVO", 1L);
    }

    @Test
    @DisplayName("CP-34: la consulta de auditoría devuelve una página de respuestas")
    void consultaAuditoria() {
        AuditoriaService servicio = mock(AuditoriaService.class);
        when(servicio.buscar(any(), anyInt(), anyInt())).thenReturn(
                new PageImpl<>(List.of(new Auditoria(1L, "X", LocalDateTime.now(), null))));
        when(servicio.listarUltimos()).thenReturn(List.of());
        AuditoriaController c = new AuditoriaController(servicio);

        PaginaResponse<?> p = c.buscar("x", null, null, null, 0, 20);

        assertEquals(1, p.totalElementos());
        assertTrue(c.ultimos().isEmpty());
    }

    @Test
    @DisplayName("CP-33: la consulta de accesos aplica el filtro de resultado")
    void consultaAccesos() {
        AccesoService servicio = mock(AccesoService.class);
        when(servicio.buscar(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(new Acceso())));
        AccesoController c = new AccesoController(servicio);

        c.buscar(null, null, "FALLIDO", null, null, 0, 20);

        ArgumentCaptor<FiltroBitacora> filtro = ArgumentCaptor.forClass(FiltroBitacora.class);
        verify(servicio).buscar(filtro.capture(), eq(0), eq(20));
        assertEquals("FALLIDO", filtro.getValue().resultado());
    }

    @Test
    @DisplayName("CP-31, CP-32 y CP-35: rutas de usuarios del Sprint 3")
    void rutasDeUsuarios() {
        UsuarioService servicio = mock(UsuarioService.class);
        Usuario u = new Usuario();
        u.setId(2L);
        when(servicio.buscar(any(), any(), any(), anyInt(), anyInt(), any(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of(u)));
        UsuarioController c = new UsuarioController(servicio);

        assertEquals(1, c.buscar("luis", null, null, 0, 10, "nombre", false).contenido().size());
        c.opciones();
        c.miPerfil(ANA);
        CambioEstadoRequest baja = new CambioEstadoRequest("INACTIVO", "Cese");
        c.cambiarEstado(2L, baja, ANA);

        verify(servicio).opciones();
        verify(servicio).perfil(1L);
        verify(servicio).cambiarEstado(2L, baja, 1L);
    }
}

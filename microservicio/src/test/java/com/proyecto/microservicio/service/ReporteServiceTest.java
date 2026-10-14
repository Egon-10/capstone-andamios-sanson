package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.StockCriticoResponse;
import com.proyecto.microservicio.dto.ValorizacionResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.MovimientoDTO;
import com.proyecto.microservicio.model.ReporteProductoDTO;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.reporte.Reporte;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CP-27 y CP-28: contenido de los reportes (HU-26, HU-27); CP-29: reporte de
 * valorización con corte (HU-28).
 */
class ReporteServiceTest {

    private MovimientoRepository movimientos;
    private ProductoRepository productos;
    private UsuarioRepository usuarios;
    private ValorizacionService valorizacion;
    private IndicadoresService indicadores;
    private UsuarioService usuarioService;
    private ReporteService servicio;

    @BeforeEach
    void preparar() {
        movimientos = mock(MovimientoRepository.class);
        productos = mock(ProductoRepository.class);
        usuarios = mock(UsuarioRepository.class);
        valorizacion = mock(ValorizacionService.class);
        indicadores = mock(IndicadoresService.class);
        usuarioService = mock(UsuarioService.class);
        servicio = new ReporteService(movimientos, productos, mock(CategoriaRepository.class), usuarios,
                mock(RolRepository.class), valorizacion, mock(KardexService.class), indicadores,
                mock(AuditoriaService.class), mock(AccesoService.class), usuarioService);

        Usuario ana = new Usuario(1L, "Ana", "ana@andamios.pe", "x", new Rol(1L, "ADMINISTRADOR"));
        ana.setApellidos("Pérez");
        when(usuarios.findById(1L)).thenReturn(Optional.of(ana));
    }

    private static MovimientoDTO movimiento(String tipo, int cantidad, String costo, String estado) {
        MovimientoDTO m = mock(MovimientoDTO.class);
        when(m.getFecha()).thenReturn(LocalDateTime.of(2026, 10, 1, 10, 0));
        when(m.getTipo()).thenReturn(tipo);
        when(m.getMotivoNombre()).thenReturn("Compra");
        when(m.getSku()).thenReturn("AND-001");
        when(m.getProducto()).thenReturn("Marco");
        when(m.getCantidad()).thenReturn(cantidad);
        when(m.getCostoUnitario()).thenReturn(new BigDecimal(costo));
        when(m.getEstado()).thenReturn(estado);
        when(m.getUsuario()).thenReturn("Luis");
        return m;
    }

    @Test
    @DisplayName("CP-27: el reporte de movimientos totaliza solo lo registrado, no lo anulado")
    void movimientosTotalizaRegistrados() {
        List<MovimientoDTO> filas = List.of(
                movimiento("ENTRADA", 10, "100.00", "REGISTRADO"),
                movimiento("SALIDA", 4, "100.00", "REGISTRADO"),
                movimiento("ENTRADA", 50, "100.00", "ANULADO"));
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 8);
        when(movimientos.buscarConFiltros(desde.atStartOfDay(), hasta.atTime(LocalTime.MAX), null)).thenReturn(filas);

        Reporte r = servicio.movimientos("TODOS", desde, hasta, 1L);

        assertEquals("Movimientos de inventario", r.titulo());
        assertEquals("Ana Pérez", r.emitidoPor());
        assertEquals(3, r.filas().size());
        assertTrue(r.filtros().contains("Periodo: del 01/10/2026 al 08/10/2026"));
        // 10 x 100 + 4 x 100 = 1400; la entrada anulada de 5000 no suma.
        assertEquals(0, new BigDecimal("1400.00").compareTo((BigDecimal) r.totales().get(7)));
        assertTrue(r.notas().get(0).contains("10 de entrada y 4 de salida"));
    }

    @Test
    @DisplayName("CP-27: rechaza un tipo de movimiento desconocido y un rango invertido")
    void movimientosValida() {
        LocalDate hoy = LocalDate.of(2026, 10, 8);
        assertThrows(ReglaNegocioException.class, () -> servicio.movimientos("TRASLADO", null, null, 1L));
        assertThrows(ReglaNegocioException.class, () -> servicio.movimientos(null, hoy, hoy.minusDays(1), 1L));
        assertEquals("ENTRADA", ReporteService.tipoMovimiento(" entrada "));
        assertNull(ReporteService.tipoMovimiento(null));
    }

    @Test
    @DisplayName("CP-27: sin movimientos el reporte sale vacío y sin fila de totales")
    void movimientosVacio() {
        when(movimientos.buscarConFiltros(null, null, "SALIDA")).thenReturn(List.of());

        Reporte r = servicio.movimientos("salida", null, null, 1L);

        assertTrue(r.filas().isEmpty());
        assertNull(r.totales());
        assertTrue(r.filtros().contains("Periodo: todo el historial"));
    }

    /** Implementación mínima de la proyección, para no crear miles de mocks. */
    private record Producto(Long getId, String getSku, String getNombre, String getCategoria,
                            String getProveedor, Integer getStock, Integer getStockMinimo,
                            Integer getPuntoReposicion, Integer getStockMaximo, BigDecimal getCostoPromedio,
                            Double getPrecio) implements ReporteProductoDTO {
    }

    @Test
    @DisplayName("CP-27: un reporte sin filtros se acota al tope de filas y lo advierte")
    void productosAcotaAlTope() {
        List<ReporteProductoDTO> muchos = new ArrayList<>();
        for (long i = 0; i <= ReporteService.MAXIMO_FILAS; i++) {
            muchos.add(new Producto(i, "SKU-" + i, "P" + i, "Andamios", null, 1, 1, null, null,
                    BigDecimal.ONE, 2.0));
        }
        when(productos.obtenerParaReporte(null)).thenReturn(muchos);

        Reporte r = servicio.productos(null, 1L);

        assertEquals(ReporteService.MAXIMO_FILAS, r.filas().size());
        assertTrue(r.notas().get(0).startsWith("Se muestran los primeros 5,000 de 5,001"));
    }

    @Test
    @DisplayName("CP-29: el reporte de valorización con corte lleva la fecha y advierte los costos estimados")
    void valorizacionConCorte() {
        LocalDate corte = LocalDate.of(2026, 9, 30);
        ValorizacionResponse v = new ValorizacionResponse(new BigDecimal("1000.00"), 10, 1,
                List.of(new ValorizacionResponse.PorCategoria("Andamios", 1, 10, new BigDecimal("1000.00"),
                        new BigDecimal("100.00"))),
                List.of(new ValorizacionResponse.PorProducto(1L, "AND-001", "Marco", "Andamios", 10,
                        new BigDecimal("100.0000"), new BigDecimal("1000.00"), false, true)),
                corte, 1);
        when(valorizacion.calcularAlCorte(corte)).thenReturn(v);

        Reporte r = servicio.valorizacion(corte, 1L);

        assertEquals("Valorización del inventario al 30/09/2026", r.titulo());
        assertEquals("Estimado", r.filas().get(0).get(6));
        assertTrue(r.notas().stream().anyMatch(n -> n.contains("costo estimado")));
        assertEquals(new BigDecimal("1000.00"), r.totales().get(5));
    }

    @Test
    @DisplayName("CP-27: el stock crítico muestra el nivel en palabras")
    void stockCritico() {
        when(indicadores.stockCritico()).thenReturn(List.of(
                new StockCriticoResponse(1L, "AND-001", "Marco", "Andamios", 0, 10, 10, 50, "AGOTADO")));

        Reporte r = servicio.stockCritico(1L);

        assertEquals("Agotado", r.filas().get(0).get(0));
        assertEquals("Crítico", ReporteService.nivel("CRITICO"));
    }

    @Test
    @DisplayName("CP-27: el reporte de usuarios no incluye documento, correo ni teléfono")
    void usuariosSinDatosPersonales() {
        Usuario luis = new Usuario(2L, "Luis", "luis@andamios.pe", "x", new Rol(3L, "ENCARGADO"));
        luis.setNumeroDocumento("12345678");
        luis.setTelefono("987654321");
        when(usuarioService.buscarParaReporte(null, null, null, ReporteService.MAXIMO_FILAS))
                .thenReturn(new PageImpl<>(List.of(luis)));

        Reporte r = servicio.usuarios(null, null, null, 1L);

        String todo = r.filas().toString() + r.columnas();
        assertFalse(todo.contains("12345678"));
        assertFalse(todo.contains("luis@andamios.pe"));
        assertFalse(todo.contains("987654321"));
        assertEquals("Activo", r.filas().get(0).get(5));
    }

    @Test
    @DisplayName("Redacta el periodo según los extremos indicados")
    void periodo() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        assertEquals("todo el historial", ReporteService.periodo(null, null));
        assertEquals("desde el 01/10/2026", ReporteService.periodo(d, null));
        assertEquals("hasta el 01/10/2026", ReporteService.periodo(null, d));
    }

    @Test
    @DisplayName("Un emisor desconocido no rompe el reporte")
    void emisorDesconocido() {
        when(indicadores.stockCritico()).thenReturn(List.of());
        when(usuarios.findById(any())).thenReturn(Optional.empty());

        assertNull(servicio.stockCritico(99L).emitidoPor());
    }
}

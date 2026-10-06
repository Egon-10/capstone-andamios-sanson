package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.IndicadoresResponse;
import com.proyecto.microservicio.dto.StockCriticoResponse;
import com.proyecto.microservicio.dto.TendenciaResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.IndicadoresProductoDTO;
import com.proyecto.microservicio.model.MovimientosDelDiaDTO;
import com.proyecto.microservicio.model.StockCriticoDTO;
import com.proyecto.microservicio.model.TendenciaDiaDTO;
import com.proyecto.microservicio.repository.AjusteInventarioRepository;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CP-24 a CP-26: panel de indicadores (HU-23), tendencia (HU-24) y stock critico (HU-25). */
class IndicadoresServiceTest {

    private ProductoRepository productos;
    private MovimientoRepository movimientos;
    private AjusteInventarioRepository ajustes;
    private IndicadoresService servicio;

    @BeforeEach
    void preparar() {
        productos = mock(ProductoRepository.class);
        movimientos = mock(MovimientoRepository.class);
        ajustes = mock(AjusteInventarioRepository.class);
        servicio = new IndicadoresService(productos, movimientos, ajustes);
    }

    // --- HU-23 ---

    @Test
    @DisplayName("CP-24: reune los indicadores del catalogo, del dia y de los ajustes")
    void indicadores() {
        IndicadoresProductoDTO p = mock(IndicadoresProductoDTO.class);
        when(p.getProductosActivos()).thenReturn(5L);
        when(p.getUnidades()).thenReturn(1130L);
        when(p.getValor()).thenReturn(new BigDecimal("365620.00"));
        when(p.getPorReponer()).thenReturn(1L);
        when(p.getSinStock()).thenReturn(0L);
        MovimientosDelDiaDTO m = mock(MovimientosDelDiaDTO.class);
        when(m.getTotal()).thenReturn(3L);
        when(m.getEntradas()).thenReturn(1L);
        when(m.getSalidas()).thenReturn(2L);
        when(productos.obtenerIndicadores()).thenReturn(p);
        when(movimientos.obtenerMovimientosDesde(any())).thenReturn(m);
        when(ajustes.countByEstado("PENDIENTE")).thenReturn(2L);

        IndicadoresResponse r = servicio.indicadores();

        assertEquals(5, r.productosActivos());
        assertEquals(1130, r.unidadesEnStock());
        assertEquals(0, new BigDecimal("365620.00").compareTo(r.valorInventario()));
        assertEquals(1, r.productosPorReponer());
        assertEquals(3, r.movimientosHoy());
        assertEquals(2, r.salidasHoy());
        assertEquals(2, r.ajustesPendientes());
        assertNotNull(r.fechaCorte());
        // Los movimientos del dia se cuentan desde la medianoche de Lima.
        verify(movimientos).obtenerMovimientosDesde(ZonaHoraria.ahora().toLocalDate().atStartOfDay());
    }

    @Test
    @DisplayName("CP-24: con la base vacia los indicadores son cero, no un error")
    void indicadoresSinDatos() {
        IndicadoresResponse r = servicio.indicadores();

        assertEquals(0, r.productosActivos());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.valorInventario()));
        assertEquals(0, r.movimientosHoy());
    }

    // --- HU-24 ---

    private static TendenciaDiaDTO dia(String fecha, long entradas, long salidas, long ue, long us) {
        TendenciaDiaDTO d = mock(TendenciaDiaDTO.class);
        when(d.getDia()).thenReturn(fecha);
        when(d.getEntradas()).thenReturn(entradas);
        when(d.getSalidas()).thenReturn(salidas);
        when(d.getUnidadesEntrada()).thenReturn(ue);
        when(d.getUnidadesSalida()).thenReturn(us);
        return d;
    }

    @Test
    @DisplayName("CP-25: completa con cero los dias sin movimientos y suma los totales")
    void tendenciaConDiasVacios() {
        LocalDate desde = LocalDate.of(2026, 6, 25);
        LocalDate hasta = LocalDate.of(2026, 6, 28);
        // Las filas se arman antes del stubbing: dia() configura sus propios mocks.
        List<TendenciaDiaDTO> filas = List.of(
                dia("2026-06-25", 2, 0, 40, 0),
                dia("2026-06-27", 1, 4, 30, 300));
        when(movimientos.obtenerTendencia(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay()))
                .thenReturn(filas);

        TendenciaResponse t = servicio.tendencia(desde, hasta);

        assertEquals(4, t.dias().size());
        assertEquals(0, t.dias().get(1).entradas());
        assertEquals(LocalDate.of(2026, 6, 26), t.dias().get(1).fecha());
        assertEquals(4, t.dias().get(2).salidas());
        assertEquals(3, t.totalEntradas());
        assertEquals(4, t.totalSalidas());
        assertEquals(70, t.unidadesEntrada());
        assertEquals(300, t.unidadesSalida());
    }

    @Test
    @DisplayName("CP-25: sin fechas cubre los ultimos treinta dias hasta hoy")
    void tendenciaPorOmision() {
        when(movimientos.obtenerTendencia(any(), any())).thenReturn(List.of());

        TendenciaResponse t = servicio.tendencia(null, null);

        assertEquals(30, t.dias().size());
        assertEquals(ZonaHoraria.ahora().toLocalDate(), t.hasta());
        assertEquals(t.hasta().minusDays(29), t.desde());
    }

    @Test
    @DisplayName("CP-25: rechaza un periodo invertido o de mas de 92 dias")
    void tendenciaPeriodoInvalido() {
        LocalDate hoy = LocalDate.of(2026, 10, 5);
        assertThrows(ReglaNegocioException.class, () -> servicio.tendencia(hoy, hoy.minusDays(1)));
        assertThrows(ReglaNegocioException.class, () -> servicio.tendencia(hoy.minusDays(92), hoy));
        verify(movimientos, never()).obtenerTendencia(any(), any());

        when(movimientos.obtenerTendencia(any(), any())).thenReturn(List.of());
        assertEquals(92, servicio.tendencia(hoy.minusDays(91), hoy).dias().size());
    }

    // --- HU-25 ---

    private static StockCriticoDTO fila(long id, String nombre, Integer stock, Integer umbral, Integer maximo) {
        StockCriticoDTO f = mock(StockCriticoDTO.class);
        when(f.getProductoId()).thenReturn(id);
        when(f.getProducto()).thenReturn(nombre);
        when(f.getStock()).thenReturn(stock);
        when(f.getUmbral()).thenReturn(umbral);
        when(f.getStockMaximo()).thenReturn(maximo);
        return f;
    }

    @Test
    @DisplayName("CP-26: ordena del mas urgente al menos urgente")
    void stockCriticoOrdenado() {
        List<StockCriticoDTO> filas = List.of(
                fila(1, "Rueda", 35, 40, null),
                fila(2, "Marco", 0, 100, 500),
                fila(3, "Baranda", 4, 10, 50),
                fila(4, "Escalera", 30, 40, null));
        when(productos.obtenerStockCritico()).thenReturn(filas);

        List<StockCriticoResponse> r = servicio.stockCritico();

        assertEquals(List.of("Marco", "Baranda", "Escalera", "Rueda"),
                r.stream().map(StockCriticoResponse::producto).toList());
        assertEquals(IndicadoresService.AGOTADO, r.get(0).nivel());
        assertEquals(IndicadoresService.CRITICO, r.get(1).nivel());
        assertEquals(IndicadoresService.BAJO, r.get(2).nivel());
        assertEquals(500, r.get(0).reponerHastaMaximo());
        assertEquals(6, r.get(1).faltanteHastaUmbral());
        assertNull(r.get(3).reponerHastaMaximo());
        assertEquals("Sin categoria", r.get(3).categoria());
    }

    @Test
    @DisplayName("CP-26: el nivel critico es la mitad del umbral o menos")
    void niveles() {
        assertEquals(IndicadoresService.AGOTADO, IndicadoresService.nivel(0, 10));
        assertEquals(IndicadoresService.AGOTADO, IndicadoresService.nivel(-3, 10));
        assertEquals(IndicadoresService.CRITICO, IndicadoresService.nivel(5, 10));
        assertEquals(IndicadoresService.BAJO, IndicadoresService.nivel(6, 10));
        assertEquals(IndicadoresService.BAJO, IndicadoresService.nivel(10, 10));
    }

    @Test
    @DisplayName("CP-26: tolera datos incompletos sin fallar")
    void datosIncompletos() {
        List<StockCriticoDTO> filas = List.of(fila(9, "X", null, null, 5));
        when(productos.obtenerStockCritico()).thenReturn(filas);

        StockCriticoResponse r = servicio.stockCritico().get(0);

        assertEquals(0, r.stock());
        assertEquals(IndicadoresService.AGOTADO, r.nivel());
        assertEquals(5, r.reponerHastaMaximo());
    }
}

package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.ValorizacionResponse;
import com.proyecto.microservicio.model.ValorizacionProductoDTO;
import com.proyecto.microservicio.repository.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CP-18: valorización del inventario al costo promedio ponderado. */
class ValorizacionServiceTest {

    private static ValorizacionProductoDTO fila(Long id, String sku, String nombre,
                                                String categoria, Integer stock,
                                                Integer minimo, Integer reposicion,
                                                String costo, String valor) {
        ValorizacionProductoDTO f = mock(ValorizacionProductoDTO.class);
        when(f.getProductoId()).thenReturn(id);
        when(f.getSku()).thenReturn(sku);
        when(f.getProducto()).thenReturn(nombre);
        when(f.getCategoria()).thenReturn(categoria);
        when(f.getStock()).thenReturn(stock);
        when(f.getStockMinimo()).thenReturn(minimo);
        when(f.getPuntoReposicion()).thenReturn(reposicion);
        when(f.getCostoPromedio()).thenReturn(new BigDecimal(costo));
        when(f.getValor()).thenReturn(new BigDecimal(valor));
        return f;
    }

    @Test
    @DisplayName("CP-18: suma el valor del inventario y lo agrupa por categoria")
    void valorizacion() {
        // Las filas se arman antes de abrir el stubbing: fila() configura sus
        // propios mocks, y hacerlo dentro de thenReturn() deja a Mockito con un
        // when() a medio terminar.
        List<ValorizacionProductoDTO> filas = List.of(
                fila(1L, "AND-001", "Marco", "Andamios", 100, 10, null, "300.00", "30000.00"),
                fila(2L, "AND-002", "Cruceta", "Andamios", 50, 10, null, "200.00", "10000.00"),
                fila(3L, "RUE-001", "Rueda", "Ruedas", 20, 40, null, "92.00", "1840.00"));

        ProductoRepository repositorio = mock(ProductoRepository.class);
        when(repositorio.obtenerValorizacion()).thenReturn(filas);

        ValorizacionResponse r = new ValorizacionService(repositorio).calcular();

        assertEquals(0, new BigDecimal("41840.00").compareTo(r.valorTotal()));
        assertEquals(170, r.unidadesTotales());
        assertEquals(3, r.productosContados());

        assertEquals(2, r.porCategoria().size());
        ValorizacionResponse.PorCategoria andamios = r.porCategoria().get(0);
        assertEquals("Andamios", andamios.categoria());
        assertEquals(2, andamios.productos());
        assertEquals(150, andamios.unidades());
        assertEquals(0, new BigDecimal("40000.00").compareTo(andamios.valor()));

        // 40000 / 41840 = 95.60 %
        assertEquals(0, new BigDecimal("95.60").compareTo(andamios.participacion()));
    }

    @Test
    @DisplayName("HU-20: marca el producto que alcanzo su umbral de reposicion")
    void marcaReposicion() {
        List<ValorizacionProductoDTO> filas = List.of(
                fila(1L, "AND-001", "Marco", "Andamios", 100, 10, null, "300.00", "30000.00"),
                fila(3L, "RUE-001", "Rueda", "Ruedas", 20, 40, null, "92.00", "1840.00"));

        ProductoRepository repositorio = mock(ProductoRepository.class);
        when(repositorio.obtenerValorizacion()).thenReturn(filas);

        ValorizacionResponse r = new ValorizacionService(repositorio).calcular();

        assertFalse(r.detalle().get(0).necesitaReposicion());
        assertTrue(r.detalle().get(1).necesitaReposicion());
    }

    @Test
    @DisplayName("HU-20: el punto de reposicion manda sobre el stock minimo")
    void elPuntoDeReposicionManda() {
        // Stock 30: por encima del minimo de 10, pero en el punto de
        // reposicion de 30, que es el umbral que se configuro.
        List<ValorizacionProductoDTO> filas = List.of(
                fila(1L, "AND-001", "Marco", "Andamios", 30, 10, 30, "300.00", "9000.00"));

        ProductoRepository repositorio = mock(ProductoRepository.class);
        when(repositorio.obtenerValorizacion()).thenReturn(filas);

        ValorizacionResponse r = new ValorizacionService(repositorio).calcular();

        assertTrue(r.detalle().get(0).necesitaReposicion());
    }

    @Test
    @DisplayName("No divide por cero cuando el inventario vale cero")
    void inventarioEnCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                ValorizacionService.participacion(BigDecimal.ZERO, BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                ValorizacionService.participacion(new BigDecimal("10"), null)));
    }

    @Test
    @DisplayName("Un producto sin categoria no rompe la agrupacion")
    void productoSinCategoria() {
        List<ValorizacionProductoDTO> filas = List.of(
                fila(1L, "AND-001", "Marco", null, 10, null, null, "300.00", "3000.00"));

        ProductoRepository repositorio = mock(ProductoRepository.class);
        when(repositorio.obtenerValorizacion()).thenReturn(filas);

        ValorizacionResponse r = new ValorizacionService(repositorio).calcular();

        assertEquals("Sin categoria", r.porCategoria().get(0).categoria());
        assertFalse(r.detalle().get(0).necesitaReposicion());
    }
}

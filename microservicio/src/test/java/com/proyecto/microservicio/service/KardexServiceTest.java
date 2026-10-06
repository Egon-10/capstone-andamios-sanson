package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.KardexResponse;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.model.KardexLineaDTO;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** CP-19: kardex por producto con saldo acumulado (HU-18). */
class KardexServiceTest {

    private MovimientoRepository movimientos;
    private ProductoRepository productos;
    private KardexService servicio;

    @BeforeEach
    void preparar() {
        movimientos = mock(MovimientoRepository.class);
        productos = mock(ProductoRepository.class);
        servicio = new KardexService(movimientos, productos);

        Producto p = new Producto();
        p.setId(5L);
        p.setNombre("Rueda");
        p.setStock(35);
        p.setCostoPromedio(new BigDecimal("92"));
        when(productos.findById(5L)).thenReturn(Optional.of(p));
    }

    private static KardexLineaDTO linea(long id, String tipo, int cantidad, int acumulado) {
        KardexLineaDTO l = mock(KardexLineaDTO.class);
        when(l.getId()).thenReturn(id);
        when(l.getTipo()).thenReturn(tipo);
        when(l.getCantidad()).thenReturn(cantidad);
        when(l.getAcumulado()).thenReturn(acumulado);
        when(l.getCostoUnitario()).thenReturn(new BigDecimal("92"));
        return l;
    }

    @Test
    @DisplayName("CP-19: reconstruye el saldo desde el stock actual y la ultima linea cuadra")
    void saldoReconstruido() {
        // Las lineas se arman antes del stubbing: linea() configura sus propios mocks.
        List<KardexLineaDTO> lineas = List.of(
                linea(4, "SALIDA", 210, -210),
                linea(5, "SALIDA", 5, -215));
        when(movimientos.obtenerNeto(5L)).thenReturn(-215);
        when(movimientos.obtenerKardex(eq(5L), isNull(), isNull())).thenReturn(lineas);

        KardexResponse k = servicio.obtener(5L, null, null);

        assertEquals(250, k.saldoInicial());
        assertEquals(40, k.lineas().get(0).saldo());
        assertEquals(35, k.lineas().get(1).saldo());
        assertEquals(k.stockActual(), k.lineas().get(1).saldo());
        assertEquals(210, k.lineas().get(0).salida());
        assertNull(k.lineas().get(0).entrada());
        assertEquals(0, new BigDecimal("19320").compareTo(k.lineas().get(0).valorizado()));
    }

    @Test
    @DisplayName("CP-19: con rango de fechas suma lo ocurrido antes del rango")
    void saldoConRango() {
        LocalDateTime desde = LocalDateTime.of(2026, 6, 27, 18, 46, 30);
        List<KardexLineaDTO> lineas = List.of(linea(5, "SALIDA", 5, -5));
        when(movimientos.obtenerNeto(5L)).thenReturn(-215);
        when(movimientos.obtenerNetoAntesDe(5L, desde)).thenReturn(-210);
        when(movimientos.obtenerKardex(5L, desde, null)).thenReturn(lineas);

        KardexResponse k = servicio.obtener(5L, desde, null);

        assertEquals(40, k.saldoInicial());
        assertEquals(35, k.lineas().get(0).saldo());
    }

    @Test
    @DisplayName("Un producto sin movimientos tiene saldo inicial igual a su stock")
    void sinMovimientos() {
        when(movimientos.obtenerNeto(5L)).thenReturn(null);
        when(movimientos.obtenerKardex(any(), any(), any())).thenReturn(List.of());

        KardexResponse k = servicio.obtener(5L, null, null);

        assertEquals(35, k.saldoInicial());
        assertTrue(k.lineas().isEmpty());
    }

    @Test
    @DisplayName("Una entrada aparece en la columna de entradas")
    void columnaDeEntrada() {
        List<KardexLineaDTO> lineas = List.of(linea(9, "ENTRADA", 30, 30));
        when(movimientos.obtenerNeto(5L)).thenReturn(30);
        when(movimientos.obtenerKardex(eq(5L), isNull(), isNull())).thenReturn(lineas);

        KardexResponse.Linea l = servicio.obtener(5L, null, null).lineas().get(0);

        assertEquals(30, l.entrada());
        assertNull(l.salida());
        assertEquals(35, l.saldo());
    }

    @Test
    @DisplayName("Un producto inexistente responde no encontrado")
    void productoInexistente() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(99L, null, null));
    }
}

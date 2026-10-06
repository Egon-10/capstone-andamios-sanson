package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.ProductoRequest;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Categoria;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CP-02 y CP-10: registro de productos con SKU único e integridad referencial. */
class ProductoServiceTest {

    private ProductoRepository productos;
    private CategoriaRepository categorias;
    private ProductoService servicio;

    @BeforeEach
    void preparar() {
        productos = mock(ProductoRepository.class);
        categorias = mock(CategoriaRepository.class);
        servicio = new ProductoService(productos, categorias, mock(ProveedorRepository.class));
        when(productos.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ProductoRequest solicitud(String sku, Long categoriaId, int stock) {
        return new ProductoRequest(sku, "Plataforma metálica 2 m", "Antideslizante", 120.0, stock, 10,
                new ProductoRequest.Referencia(categoriaId), null);
    }

    @Test
    @DisplayName("CP-10: registra el producto con el SKU normalizado en mayúsculas")
    void registroValido() {
        when(categorias.findById(1L)).thenReturn(Optional.of(new Categoria(1L, "Plataformas")));

        Producto p = servicio.registrar(solicitud("pla-200", 1L, 50));

        assertEquals("PLA-200", p.getSku());
        assertEquals(50, p.getStock());
        assertEquals("Plataformas", p.getCategoria().getNombre());
    }

    @Test
    @DisplayName("CP-10: rechaza un SKU duplicado y no guarda el producto")
    void skuDuplicado() {
        when(productos.existsBySkuIgnoreCase("PLA-200")).thenReturn(true);

        ConflictoException ex = assertThrows(ConflictoException.class,
                () -> servicio.registrar(solicitud("pla-200", 1L, 50)));

        assertEquals("sku", ex.getCampo());
        verify(productos, never()).save(any());
    }

    @Test
    @DisplayName("CP-02: rechaza un producto cuya categoría no existe")
    void categoriaInexistente() {
        when(categorias.findById(99L)).thenReturn(Optional.empty());

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(solicitud("PLA-201", 99L, 5)));

        assertEquals("categoria", ex.getCampo());
        verify(productos, never()).save(any());
    }

    @Test
    @DisplayName("La edición no modifica el stock, que solo cambia con movimientos")
    void edicionNoCambiaStock() {
        Producto existente = new Producto();
        existente.setId(8L);
        existente.setStock(40);
        when(productos.findById(8L)).thenReturn(Optional.of(existente));
        when(categorias.findById(1L)).thenReturn(Optional.of(new Categoria(1L, "Plataformas")));

        Producto p = servicio.actualizar(8L, solicitud("PLA-200", 1L, 999));

        assertEquals(40, p.getStock());
        assertEquals("PLA-200", p.getSku());
    }
}

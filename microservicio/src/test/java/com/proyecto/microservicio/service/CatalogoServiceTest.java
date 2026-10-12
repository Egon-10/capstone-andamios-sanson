package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.CategoriaRequest;
import com.proyecto.microservicio.dto.ProveedorRequest;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.model.Categoria;
import com.proyecto.microservicio.model.Proveedor;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CP-12: catalogo maestro de categorias y proveedores (HU-11). */
class CatalogoServiceTest {

    private CategoriaRepository categorias;
    private ProveedorRepository proveedores;
    private CategoriaService categoriaService;
    private ProveedorService proveedorService;

    @BeforeEach
    void preparar() {
        categorias = mock(CategoriaRepository.class);
        proveedores = mock(ProveedorRepository.class);
        categoriaService = new CategoriaService(categorias, mock(AuditoriaService.class));
        proveedorService = new ProveedorService(proveedores, mock(AuditoriaService.class));
        when(categorias.save(any(Categoria.class))).thenAnswer(i -> i.getArgument(0));
        when(proveedores.save(any(Proveedor.class))).thenAnswer(i -> i.getArgument(0));
    }

    // --- categorias ---

    @Test
    @DisplayName("CP-12: lista solo las categorias activas, salvo que se pidan todas")
    void listarCategorias() {
        List<Categoria> activas = List.of(new Categoria(1L, "Andamios"));
        List<Categoria> todas = List.of(new Categoria(1L, "Andamios"), new Categoria(2L, "Retirada"));
        when(categorias.findByActivoTrueOrderByNombreAsc()).thenReturn(activas);
        when(categorias.findAllByOrderByNombreAsc()).thenReturn(todas);

        assertEquals(1, categoriaService.listar(false).size());
        assertEquals(2, categoriaService.listar(true).size());
    }

    @Test
    @DisplayName("CP-12: registra la categoria activa, con el nombre recortado")
    void registrarCategoria() {
        Categoria c = categoriaService.registrar(new CategoriaRequest("  Andamios  ", "  "));

        assertEquals("Andamios", c.getNombre());
        assertNull(c.getDescripcion());
        assertTrue(c.isActivo());
        assertNotNull(c.getFechaCreacion());
    }

    @Test
    @DisplayName("CP-12: rechaza un nombre de categoria repetido")
    void categoriaRepetida() {
        when(categorias.existsByNombreIgnoreCase("Andamios")).thenReturn(true);
        ConflictoException ex = assertThrows(ConflictoException.class,
                () -> categoriaService.registrar(new CategoriaRequest("Andamios", null)));
        assertEquals("nombre", ex.getCampo());
    }

    @Test
    @DisplayName("CP-12: al editar, el nombre no puede chocar con otra categoria")
    void actualizarCategoria() {
        Categoria existente = new Categoria(1L, "Andamios");
        when(categorias.findById(1L)).thenReturn(Optional.of(existente));

        Categoria c = categoriaService.actualizar(1L, new CategoriaRequest("Andamios ACRO", "Galvanizados"));
        assertEquals("Andamios ACRO", c.getNombre());
        assertEquals("Galvanizados", c.getDescripcion());

        when(categorias.existsByNombreIgnoreCaseAndIdNot("Ruedas", 1L)).thenReturn(true);
        assertThrows(ConflictoException.class,
                () -> categoriaService.actualizar(1L, new CategoriaRequest("Ruedas", null)));
    }

    @Test
    @DisplayName("CP-12: dar de baja no borra la categoria, la desactiva")
    void bajaLogicaCategoria() {
        Categoria existente = new Categoria(1L, "Andamios");
        when(categorias.findById(1L)).thenReturn(Optional.of(existente));

        assertFalse(categoriaService.darDeBaja(1L).isActivo());
        verify(categorias, never()).delete(any());
        verify(categorias, never()).deleteById(any());

        assertTrue(categoriaService.reactivar(1L).isActivo());
    }

    @Test
    @DisplayName("Una categoria inexistente responde no encontrado")
    void categoriaInexistente() {
        assertThrows(RecursoNoEncontradoException.class, () -> categoriaService.obtener(9L));
    }

    // --- proveedores ---

    private static ProveedorRequest proveedor(String ruc) {
        return new ProveedorRequest(" Aceros Arequipa ", ruc, "Av. Industrial 123", "987654321",
                "ventas@aceros.pe");
    }

    @Test
    @DisplayName("CP-12: registra el proveedor activo")
    void registrarProveedor() {
        Proveedor p = proveedorService.registrar(proveedor("20370146994"));

        assertEquals("Aceros Arequipa", p.getNombre());
        assertEquals("20370146994", p.getRuc());
        assertTrue(p.isActivo());
        assertNotNull(p.getFechaCreacion());
    }

    @Test
    @DisplayName("CP-12: el RUC es opcional, pero si se informa no puede repetirse")
    void rucRepetido() {
        assertNull(proveedorService.registrar(proveedor("  ")).getRuc());

        when(proveedores.existsByRuc("20370146994")).thenReturn(true);
        ConflictoException ex = assertThrows(ConflictoException.class,
                () -> proveedorService.registrar(proveedor("20370146994")));
        assertEquals("ruc", ex.getCampo());
    }

    @Test
    @DisplayName("CP-12: al editar, el RUC no puede chocar con otro proveedor")
    void actualizarProveedor() {
        Proveedor existente = new Proveedor();
        existente.setId(1L);
        when(proveedores.findById(1L)).thenReturn(Optional.of(existente));

        assertEquals("Aceros Arequipa", proveedorService.actualizar(1L, proveedor("20370146994")).getNombre());

        when(proveedores.existsByRucAndIdNot("20603128690", 1L)).thenReturn(true);
        assertThrows(ConflictoException.class,
                () -> proveedorService.actualizar(1L, proveedor("20603128690")));
    }

    @Test
    @DisplayName("CP-12: dar de baja no borra el proveedor")
    void bajaLogicaProveedor() {
        Proveedor existente = new Proveedor();
        existente.setId(1L);
        when(proveedores.findById(1L)).thenReturn(Optional.of(existente));
        when(proveedores.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of());
        when(proveedores.findAllByOrderByNombreAsc()).thenReturn(List.of(existente));

        assertFalse(proveedorService.darDeBaja(1L).isActivo());
        verify(proveedores, never()).deleteById(any());
        assertTrue(proveedorService.reactivar(1L).isActivo());
        assertEquals(0, proveedorService.listar(false).size());
        assertEquals(1, proveedorService.listar(true).size());
        assertThrows(RecursoNoEncontradoException.class, () -> proveedorService.obtener(9L));
    }
}

package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.FichaProductoResponse;
import com.proyecto.microservicio.dto.FiltroProductos;
import com.proyecto.microservicio.dto.KardexResponse;
import com.proyecto.microservicio.dto.ProductoRequest;
import com.proyecto.microservicio.dto.UmbralesRequest;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.AjusteInventario;
import com.proyecto.microservicio.model.Categoria;
import com.proyecto.microservicio.model.EstadosAjuste;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.Proveedor;
import com.proyecto.microservicio.model.ResumenProductoDTO;
import com.proyecto.microservicio.repository.AjusteInventarioRepository;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** CP-21 a CP-23: busqueda paginada, ficha y umbrales (HU-20 a HU-22). */
class ProductoServiceSprint2Test {

    private ProductoRepository productos;
    private CategoriaRepository categorias;
    private ProveedorRepository proveedores;
    private MovimientoRepository movimientos;
    private AjusteInventarioRepository ajustes;
    private KardexService kardex;
    private ProductoService servicio;
    private Producto marco;

    @BeforeEach
    void preparar() {
        productos = mock(ProductoRepository.class);
        categorias = mock(CategoriaRepository.class);
        proveedores = mock(ProveedorRepository.class);
        movimientos = mock(MovimientoRepository.class);
        ajustes = mock(AjusteInventarioRepository.class);
        kardex = mock(KardexService.class);
        servicio = new ProductoService(productos, categorias, proveedores, movimientos, ajustes, kardex);

        marco = new Producto();
        marco.setId(1L);
        marco.setSku("AND-1");
        marco.setNombre("Marco");
        marco.setStock(50);
        marco.setStockMinimo(10);
        marco.setStockMaximo(200);
        marco.setPrecio(300.0);
        marco.setCostoPromedio(new BigDecimal("280"));
        when(productos.findById(1L)).thenReturn(Optional.of(marco));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
    }

    // --- HU-21 ---

    @Test
    @DisplayName("CP-22: arma la paginacion acotando lo que llega por la URL")
    void paginacion() {
        Pageable p = ProductoService.paginacion(-3, 5000, "stock", "DESC");
        assertEquals(0, p.getPageNumber());
        assertEquals(100, p.getPageSize());
        assertEquals(Sort.Direction.DESC, p.getSort().getOrderFor("stock").getDirection());

        Pageable q = ProductoService.paginacion(2, 0, "categoria.nombre; DROP", "asc");
        assertEquals(1, q.getPageSize());
        assertNotNull(q.getSort().getOrderFor("nombre"), "un campo no permitido vuelve a nombre");
        assertNull(q.getSort().getOrderFor("categoria.nombre; DROP"));
    }

    @Test
    @DisplayName("CP-22: busca por texto en minusculas y con comodines")
    void buscarTexto() {
        Page<Producto> vacia = new PageImpl<>(List.of());
        when(productos.buscar(any(), any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(vacia);

        servicio.buscar(new FiltroProductos("  PLAtaforma ", 3L, null, true, false, 0, 20, "nombre", "asc"));
        verify(productos).buscar(eq("%plataforma%"), eq(3L), isNull(), eq(true), eq(false), any());

        servicio.buscar(new FiltroProductos("   ", null, null, false, true, 0, 20, null, null));
        verify(productos).buscar(isNull(), isNull(), isNull(), eq(false), eq(true), any());
    }

    // --- HU-20 ---

    @Test
    @DisplayName("CP-21: actualiza los umbrales sin tocar el resto de la ficha")
    void umbrales() {
        Producto p = servicio.actualizarUmbrales(1L, new UmbralesRequest(null, 15, null));

        assertEquals(10, p.getStockMinimo());
        assertEquals(15, p.getPuntoReposicion());
        assertEquals(200, p.getStockMaximo());
        assertEquals("Marco", p.getNombre());
        assertEquals(50, p.getStock());
    }

    @Test
    @DisplayName("CP-21: el maximo no puede quedar por debajo del minimo")
    void maximoMenorQueMinimo() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizarUmbrales(1L, new UmbralesRequest(300, null, null)));
        assertEquals("stockMaximo", ex.getCampo());
    }

    @Test
    @DisplayName("CP-21: el punto de reposicion no puede superar el maximo")
    void reposicionSobreElMaximo() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizarUmbrales(1L, new UmbralesRequest(null, 250, null)));
        assertEquals("puntoReposicion", ex.getCampo());
    }

    // --- HU-22 ---

    @Test
    @DisplayName("CP-23: arma la ficha con existencias, valorizacion, resumen y ultimos movimientos")
    void ficha() {
        Categoria cat = new Categoria(2L, "Andamios");
        Proveedor prov = new Proveedor();
        prov.setId(4L);
        prov.setNombre("Aceros");
        marco.setCategoria(cat);
        marco.setProveedor(prov);

        ResumenProductoDTO resumen = mock(ResumenProductoDTO.class);
        when(resumen.getMovimientos()).thenReturn(12L);
        when(resumen.getEntradas()).thenReturn(5L);
        when(resumen.getSalidas()).thenReturn(7L);
        when(resumen.getUnidadesIngresadas()).thenReturn(80);
        when(resumen.getUnidadesRetiradas()).thenReturn(30);
        when(movimientos.obtenerResumen(1L)).thenReturn(resumen);

        List<KardexResponse.Linea> lineas = new ArrayList<>();
        for (long i = 1; i <= 15; i++) {
            lineas.add(new KardexResponse.Linea(i, null, "ENTRADA", "COMPRA", "Compra", null,
                    "REGISTRADO", 1, null, BigDecimal.ONE, BigDecimal.ONE, (int) i, "Ana"));
        }
        KardexResponse k = new KardexResponse(1L, "AND-1", "Marco", 50, BigDecimal.ONE,
                BigDecimal.ONE, 0, lineas);
        when(kardex.obtener(1L, null, null)).thenReturn(k);

        AjusteInventario pendiente = new AjusteInventario();
        pendiente.setEstado(EstadosAjuste.PENDIENTE);
        AjusteInventario aprobado = new AjusteInventario();
        aprobado.setEstado(EstadosAjuste.APROBADO);
        when(ajustes.findByProductoIdOrderByFechaSolicitudDesc(1L)).thenReturn(List.of(pendiente, aprobado));

        FichaProductoResponse f = servicio.ficha(1L, 10);

        assertEquals("Andamios", f.categoria().nombre());
        assertEquals("Aceros", f.proveedor().nombre());
        assertEquals(150, f.existencias().faltanteHastaElMaximo());
        assertFalse(f.existencias().necesitaReposicion());
        assertEquals(0, new BigDecimal("14000").compareTo(f.valorizacion().valorizado()));
        assertEquals(12, f.resumen().movimientos());
        assertEquals(10, f.ultimosMovimientos().size());
        assertEquals(15L, f.ultimosMovimientos().get(9).id(), "los ultimos son los mas recientes");
        assertEquals(1, f.ajustesPendientes());
    }

    @Test
    @DisplayName("CP-23: la ficha funciona para un producto sin historial ni maximo")
    void fichaSinHistorial() {
        marco.setStockMaximo(null);
        when(movimientos.obtenerResumen(1L)).thenReturn(null);
        when(kardex.obtener(1L, null, null)).thenReturn(
                new KardexResponse(1L, "AND-1", "Marco", 50, BigDecimal.ZERO, BigDecimal.ZERO, 50, List.of()));
        when(ajustes.findByProductoIdOrderByFechaSolicitudDesc(1L)).thenReturn(List.of());

        FichaProductoResponse f = servicio.ficha(1L, 0);

        assertNull(f.categoria());
        assertNull(f.proveedor());
        assertNull(f.existencias().faltanteHastaElMaximo());
        assertEquals(0, f.resumen().movimientos());
        assertTrue(f.ultimosMovimientos().isEmpty());
    }

    // --- HU-11 aplicado a productos ---

    private static ProductoRequest solicitud(Long categoriaId, Long proveedorId) {
        return new ProductoRequest("AND-2", "Cruceta", null, 120.0, 10, 2,
                new ProductoRequest.Referencia(categoriaId),
                proveedorId == null ? null : new ProductoRequest.Referencia(proveedorId));
    }

    @Test
    @DisplayName("CP-12: no se asigna una categoria dada de baja a un producto nuevo")
    void categoriaDeBaja() {
        Categoria retirada = new Categoria(7L, "Retirada");
        retirada.setActivo(false);
        when(categorias.findById(7L)).thenReturn(Optional.of(retirada));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(solicitud(7L, null)));
        assertEquals("categoria", ex.getCampo());
    }

    @Test
    @DisplayName("CP-12: un producto que ya tenia la categoria dada de baja se puede editar")
    void conservaCategoriaDeBaja() {
        Categoria retirada = new Categoria(7L, "Retirada");
        retirada.setActivo(false);
        marco.setCategoria(retirada);
        when(categorias.findById(7L)).thenReturn(Optional.of(retirada));

        assertEquals(retirada, servicio.actualizar(1L, solicitud(7L, null)).getCategoria());
    }

    @Test
    @DisplayName("CP-12: no se asigna un proveedor dado de baja")
    void proveedorDeBaja() {
        when(categorias.findById(2L)).thenReturn(Optional.of(new Categoria(2L, "Andamios")));
        Proveedor retirado = new Proveedor();
        retirado.setId(9L);
        retirado.setNombre("Viejo");
        retirado.setActivo(false);
        when(proveedores.findById(9L)).thenReturn(Optional.of(retirado));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(solicitud(2L, 9L)));
        assertEquals("proveedor", ex.getCampo());

        marco.setProveedor(retirado);
        assertEquals(retirado, servicio.actualizar(1L, solicitud(2L, 9L)).getProveedor());
    }
}

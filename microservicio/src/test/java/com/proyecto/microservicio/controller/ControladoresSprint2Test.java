package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.AjusteRequest;
import com.proyecto.microservicio.dto.AnulacionRequest;
import com.proyecto.microservicio.dto.CargaMasivaResponse;
import com.proyecto.microservicio.dto.CategoriaRequest;
import com.proyecto.microservicio.dto.FiltroProductos;
import com.proyecto.microservicio.dto.MotivoResponse;
import com.proyecto.microservicio.dto.MovimientoRequest;
import com.proyecto.microservicio.dto.PaginaResponse;
import com.proyecto.microservicio.dto.ProductoResumen;
import com.proyecto.microservicio.dto.ProveedorRequest;
import com.proyecto.microservicio.dto.RechazoRequest;
import com.proyecto.microservicio.dto.UmbralesRequest;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.AjusteInventario;
import com.proyecto.microservicio.model.Categoria;
import com.proyecto.microservicio.model.MotivoMovimiento;
import com.proyecto.microservicio.model.Movimiento;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.Proveedor;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.service.AjusteInventarioService;
import com.proyecto.microservicio.service.CargaMasivaService;
import com.proyecto.microservicio.service.CategoriaService;
import com.proyecto.microservicio.service.KardexService;
import com.proyecto.microservicio.service.MotivoMovimientoService;
import com.proyecto.microservicio.service.MovimientoService;
import com.proyecto.microservicio.service.ProductoService;
import com.proyecto.microservicio.service.ProveedorService;
import com.proyecto.microservicio.service.ValorizacionService;
import com.proyecto.microservicio.support.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Controladores del Sprint 2.
 *
 * La prueba central es que la identidad de quien registra un movimiento, un
 * conteo o una anulacion se toma del token y se pasa tal cual al servicio
 * (CR-04): el controlador no tiene otra fuente de la que sacarla.
 */
class ControladoresSprint2Test {

    private static final long USUARIO = 7L;

    private static Movimiento movimiento(long id) {
        Producto p = new Producto();
        p.setId(1L);
        p.setNombre("Marco");
        MotivoMovimiento m = new MotivoMovimiento();
        m.setCodigo("COMPRA");
        m.setNombre("Compra");
        Usuario u = new Usuario();
        u.setNombre("Ana");
        u.setApellidos("Rios");
        Movimiento mov = new Movimiento();
        mov.setId(id);
        mov.setTipo("ENTRADA");
        mov.setCantidad(5);
        mov.setCostoUnitario(new BigDecimal("300"));
        mov.setProducto(p);
        mov.setMotivo(m);
        mov.setUsuario(u);
        return mov;
    }

    // --- movimientos ---

    @Test
    @DisplayName("CR-04: entrada, salida y anulacion usan el usuario del token")
    void movimientosConUsuarioDelToken() {
        MovimientoService servicio = mock(MovimientoService.class);
        MovimientoController c = new MovimientoController(servicio);
        MovimientoRequest s = new MovimientoRequest(1L, 5, "COMPRA", null, null);
        when(servicio.registrarEntrada(s, USUARIO)).thenReturn(movimiento(10));
        when(servicio.registrarSalida(s, USUARIO)).thenReturn(movimiento(11));
        when(servicio.anular(eq(4L), any(), eq(USUARIO))).thenReturn(movimiento(12));
        when(servicio.obtener(10L)).thenReturn(movimiento(10));

        ResponseEntity<?> entrada = c.entrada(s, Tokens.deUsuario(USUARIO));
        ResponseEntity<?> salida = c.salida(s, Tokens.deUsuario(USUARIO));
        ResponseEntity<?> anulacion = c.anular(4L, new AnulacionRequest("Error de digitacion"),
                Tokens.deUsuario(USUARIO));

        assertEquals(201, entrada.getStatusCode().value());
        assertEquals(201, salida.getStatusCode().value());
        assertEquals(201, anulacion.getStatusCode().value());
        assertEquals("Ana Rios", c.obtener(10L).usuario());
        assertEquals(0, new BigDecimal("1500").compareTo(c.obtener(10L).valorizado()));
        verify(servicio).registrarEntrada(s, USUARIO);
        verify(servicio).registrarSalida(s, USUARIO);
    }

    @Test
    @DisplayName("El filtro convierte las fechas al inicio y al final del dia")
    void filtroDeFechas() {
        MovimientoService servicio = mock(MovimientoService.class);
        MovimientoController c = new MovimientoController(servicio);

        c.filtrar("2026-06-01", "2026-06-30", "SALIDA");
        verify(servicio).filtrarMovimientos(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 30, 0, 0).with(LocalTime.MAX),
                "SALIDA");

        c.filtrar(" ", null, " ");
        verify(servicio).filtrarMovimientos(null, null, null);

        c.listar();
        verify(servicio).listarMovimientos();
    }

    @Test
    @DisplayName("Una fecha mal escrita es una solicitud invalida, no un error 500")
    void fechaInvalida() {
        MovimientoController c = new MovimientoController(mock(MovimientoService.class));
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> c.filtrar("01/06/2026", null, null));
        assertEquals("fecha", ex.getCampo());
    }

    // --- motivos ---

    @Test
    @DisplayName("HU-14: lista los motivos, todos o por tipo")
    void motivos() {
        MotivoMovimientoService servicio = mock(MotivoMovimientoService.class);
        MotivoMovimiento compra = new MotivoMovimiento();
        compra.setCodigo("COMPRA");
        compra.setAplicaA("ENTRADA");
        when(servicio.listarSeleccionables()).thenReturn(List.of(compra));
        when(servicio.listarPorTipo("ENTRADA")).thenReturn(List.of(compra));
        MotivoMovimientoController c = new MotivoMovimientoController(servicio);

        List<MotivoResponse> todos = c.listar(null);
        assertEquals("COMPRA", todos.get(0).codigo());
        assertEquals(1, c.listar(" entrada ").size());
        assertEquals(1, c.listar("").size());
        assertThrows(ReglaNegocioException.class, () -> c.listar("TRASLADO"));
    }

    // --- ajustes ---

    @Test
    @DisplayName("CR-04: quien cuenta y quien aprueba se toman del token")
    void ajustesConUsuarioDelToken() {
        AjusteInventarioService servicio = mock(AjusteInventarioService.class);
        AjusteInventario a = new AjusteInventario();
        a.setId(9L);
        a.setEstado("PENDIENTE");
        when(servicio.registrar(any(), eq(USUARIO))).thenReturn(a);
        when(servicio.aprobar(9L, USUARIO)).thenReturn(a);
        when(servicio.rechazar(eq(9L), any(), eq(USUARIO))).thenReturn(a);
        when(servicio.obtener(9L)).thenReturn(a);
        when(servicio.listar()).thenReturn(List.of(a));
        when(servicio.listarPendientes()).thenReturn(List.of(a));
        when(servicio.listarPorProducto(1L)).thenReturn(List.of());
        AjusteInventarioController c = new AjusteInventarioController(servicio);

        assertEquals(201, c.registrar(new AjusteRequest(1L, 95, null), Tokens.deUsuario(USUARIO))
                .getStatusCode().value());
        assertEquals(9L, c.aprobar(9L, Tokens.deUsuario(USUARIO)).id());
        assertEquals(9L, c.rechazar(9L, new RechazoRequest("No se conto todo"),
                Tokens.deUsuario(USUARIO)).id());
        assertEquals(9L, c.obtener(9L).id());
        assertEquals(1, c.listar(null, null).size());
        assertEquals(1, c.listar("pendiente", null).size());
        assertEquals(0, c.listar(null, 1L).size());
        verify(servicio).aprobar(9L, USUARIO);
    }

    // --- inventario ---

    @Test
    @DisplayName("HU-17 y HU-18: valorizacion, reposicion y kardex con fechas")
    void inventario() {
        ValorizacionService valorizacion = mock(ValorizacionService.class);
        KardexService kardex = mock(KardexService.class);
        InventarioController c = new InventarioController(valorizacion, kardex);

        c.valorizacion();
        c.porReponer();
        c.kardex(5L, "2026-01-01", "2026-01-31");
        c.kardex(5L, null, " ");

        verify(valorizacion).calcular();
        verify(valorizacion).porReponer();
        verify(kardex).obtener(5L, LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 31, 0, 0).with(LocalTime.MAX));
        verify(kardex).obtener(5L, null, null);
        assertThrows(ReglaNegocioException.class, () -> c.kardex(5L, "ayer", null));
    }

    // --- productos ---

    @Test
    @DisplayName("HU-21, HU-22 y HU-20: busqueda, ficha y umbrales")
    void productos() {
        ProductoService servicio = mock(ProductoService.class);
        ProductoController c = new ProductoController(servicio, mock(CargaMasivaService.class));
        Producto p = new Producto();
        p.setId(1L);
        p.setNombre("Marco");
        p.setStock(5);
        p.setStockMinimo(10);
        when(servicio.buscar(any(FiltroProductos.class))).thenReturn(new PageImpl<>(List.of(p)));

        PaginaResponse<ProductoResumen> pagina = c.buscar("marco", null, null, true, false, 0, 20,
                "nombre", "asc");

        assertEquals(1, pagina.totalElementos());
        assertTrue(pagina.contenido().get(0).necesitaReposicion());
        verify(servicio).buscar(new FiltroProductos("marco", null, null, true, false, 0, 20, "nombre", "asc"));

        c.ficha(1L, 10);
        verify(servicio).ficha(1L, 10);
        c.actualizarUmbrales(1L, new UmbralesRequest(1, 2, 3));
        verify(servicio).actualizarUmbrales(eq(1L), any());
    }

    @Test
    @DisplayName("HU-19: la carga masiva lee el archivo y pasa el usuario del token")
    void cargaMasiva() {
        CargaMasivaService carga = mock(CargaMasivaService.class);
        ProductoController c = new ProductoController(mock(ProductoService.class), carga);
        when(carga.cargar(anyString(), anyString(), anyBoolean(), anyLong()))
                .thenReturn(new CargaMasivaResponse("PARCIAL", true, 1, 1, 0, false, List.of(), List.of()));
        MockMultipartFile archivo = new MockMultipartFile("archivo", "p.csv", "text/csv",
                "sku,nombre".getBytes(StandardCharsets.UTF_8));

        c.cargaMasiva(archivo, "PARCIAL", true, Tokens.deUsuario(USUARIO));

        verify(carga).cargar("sku,nombre", "PARCIAL", true, USUARIO);
    }

    @Test
    @DisplayName("HU-19: rechaza un archivo vacio, demasiado grande o ilegible")
    void cargaMasivaArchivoInvalido() throws IOException {
        ProductoController c = new ProductoController(mock(ProductoService.class),
                mock(CargaMasivaService.class));

        assertThrows(ReglaNegocioException.class, () -> c.cargaMasiva(
                new MockMultipartFile("archivo", new byte[0]), "PARCIAL", false, Tokens.deUsuario(USUARIO)));
        assertThrows(ReglaNegocioException.class, () -> c.cargaMasiva(
                null, "PARCIAL", false, Tokens.deUsuario(USUARIO)));

        MultipartFile grande = mock(MultipartFile.class);
        when(grande.isEmpty()).thenReturn(false);
        when(grande.getSize()).thenReturn(3L * 1024 * 1024);
        assertThrows(ReglaNegocioException.class,
                () -> c.cargaMasiva(grande, "PARCIAL", false, Tokens.deUsuario(USUARIO)));

        MultipartFile roto = mock(MultipartFile.class);
        when(roto.isEmpty()).thenReturn(false);
        when(roto.getSize()).thenReturn(10L);
        when(roto.getBytes()).thenThrow(new IOException("disco"));
        assertThrows(ReglaNegocioException.class,
                () -> c.cargaMasiva(roto, "PARCIAL", false, Tokens.deUsuario(USUARIO)));
    }

    // --- catalogo maestro ---

    @Test
    @DisplayName("HU-11: DELETE de categoria y proveedor hace baja logica")
    void catalogo() {
        CategoriaService categorias = mock(CategoriaService.class);
        CategoriaController cc = new CategoriaController(categorias);
        CategoriaRequest cr = new CategoriaRequest("Andamios", null);
        when(categorias.registrar(cr)).thenReturn(new Categoria(1L, "Andamios"));

        assertEquals(201, cc.registrar(cr).getStatusCode().value());
        assertEquals(204, cc.darDeBaja(1L).getStatusCode().value());
        cc.listar(true);
        cc.obtener(1L);
        cc.actualizar(1L, cr);
        cc.reactivar(1L);
        verify(categorias).darDeBaja(1L);
        verify(categorias).listar(true);
        verify(categorias).reactivar(1L);

        ProveedorService proveedores = mock(ProveedorService.class);
        ProveedorController pc = new ProveedorController(proveedores);
        ProveedorRequest pr = new ProveedorRequest("Aceros", null, null, null, null);
        when(proveedores.registrar(pr)).thenReturn(new Proveedor());

        assertEquals(201, pc.registrar(pr).getStatusCode().value());
        assertEquals(204, pc.darDeBaja(2L).getStatusCode().value());
        pc.listar(false);
        pc.obtener(2L);
        pc.actualizar(2L, pr);
        pc.reactivar(2L);
        verify(proveedores).darDeBaja(2L);
        verify(proveedores).listar(false);
    }
}

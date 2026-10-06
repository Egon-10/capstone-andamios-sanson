package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.AnulacionRequest;
import com.proyecto.microservicio.dto.MovimientoRequest;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.EstadosMovimiento;
import com.proyecto.microservicio.model.MotivoMovimiento;
import com.proyecto.microservicio.model.Movimiento;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.TiposMovimiento;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CP-13, CP-14 y CP-16: registro de entradas y salidas, y anulación.
 */
class MovimientoServiceTest {

    private MovimientoRepository movimientos;
    private ProductoRepository productos;
    private UsuarioRepository usuarios;
    private MotivoMovimientoService motivos;
    private AuditoriaService auditoria;
    private MovimientoService servicio;

    private Producto producto;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        movimientos = mock(MovimientoRepository.class);
        productos = mock(ProductoRepository.class);
        usuarios = mock(UsuarioRepository.class);
        motivos = mock(MotivoMovimientoService.class);
        auditoria = mock(AuditoriaService.class);
        servicio = new MovimientoService(movimientos, productos, usuarios, motivos, auditoria);

        producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Marco de andamio");
        producto.setStock(100);
        producto.setCostoPromedio(new BigDecimal("300.0000"));
        producto.setActivo(true);

        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setNombre("Ana");

        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        when(movimientos.save(any(Movimiento.class))).thenAnswer(i -> {
            Movimiento m = i.getArgument(0);
            if (m.getId() == null) {
                m.setId(99L);
            }
            return m;
        });
        when(motivos.validar(anyString(), anyString(), any()))
                .thenAnswer(i -> motivo(i.getArgument(0), i.getArgument(1)));
        when(motivos.motivoDeAnulacion(anyString()))
                .thenAnswer(i -> motivo("ANULACION_" + i.getArgument(0), i.getArgument(0)));
    }

    private static MotivoMovimiento motivo(String codigo, String aplicaA) {
        MotivoMovimiento m = new MotivoMovimiento();
        m.setCodigo(codigo);
        m.setNombre(codigo);
        m.setAplicaA(aplicaA);
        m.setActivo(true);
        return m;
    }

    private static MovimientoRequest solicitud(int cantidad, String motivo, BigDecimal costo) {
        return new MovimientoRequest(1L, cantidad, motivo, null, costo);
    }

    // --- HU-12: entrada ---

    @Test
    @DisplayName("CP-13: la entrada suma al stock y deja el saldo en el asiento")
    void entradaSumaStock() {
        Movimiento m = servicio.registrarEntrada(
                solicitud(30, "COMPRA", new BigDecimal("320")), 7L);

        assertEquals(130, producto.getStock());
        assertEquals(TiposMovimiento.ENTRADA, m.getTipo());
        assertEquals(130, m.getSaldoResultante());
        assertEquals(EstadosMovimiento.REGISTRADO, m.getEstado());
        assertEquals(usuario, m.getUsuario());
    }

    @Test
    @DisplayName("CP-13: la identidad del movimiento sale del usuario recibido, no de la solicitud")
    void laIdentidadNoVieneDeLaSolicitud() {
        // La solicitud no tiene ningun campo de usuario: el registro solo puede
        // atribuirse a quien el servicio recibe como parametro, que el
        // controlador toma del token (CR-04).
        Movimiento m = servicio.registrarEntrada(solicitud(5, "COMPRA", null), 7L);

        assertEquals(7L, m.getUsuario().getId());
        verify(usuarios).findById(7L);
    }

    @Test
    @DisplayName("HU-17: la entrada recalcula el costo promedio ponderado")
    void entradaRecalculaPromedio() {
        // 100 unidades a 300 mas 50 a 360 = (30000 + 18000) / 150 = 320
        servicio.registrarEntrada(solicitud(50, "COMPRA", new BigDecimal("360")), 7L);

        assertEquals(0, new BigDecimal("320.0000").compareTo(producto.getCostoPromedio()));
    }

    @Test
    @DisplayName("HU-17: una entrada sin costo informado reingresa al promedio vigente")
    void entradaSinCostoUsaElPromedio() {
        Movimiento m = servicio.registrarEntrada(solicitud(10, "DEVOLUCION_OBRA", null), 7L);

        assertEquals(0, new BigDecimal("300.0000").compareTo(m.getCostoUnitario()));
        assertEquals(0, new BigDecimal("300.0000").compareTo(producto.getCostoPromedio()));
    }

    // --- HU-13: salida ---

    @Test
    @DisplayName("CP-14: la salida descuenta del stock y se valoriza al promedio")
    void salidaDescuentaStock() {
        Movimiento m = servicio.registrarSalida(solicitud(40, "ALQUILER", null), 7L);

        assertEquals(60, producto.getStock());
        assertEquals(60, m.getSaldoResultante());
        assertEquals(0, new BigDecimal("300.0000").compareTo(m.getCostoUnitario()));
        // La salida no altera el promedio ponderado.
        assertEquals(0, new BigDecimal("300.0000").compareTo(producto.getCostoPromedio()));
    }

    @Test
    @DisplayName("CP-14: rechaza la salida cuando el stock no alcanza y no guarda nada")
    void salidaSinStock() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarSalida(solicitud(101, "ALQUILER", null), 7L));

        assertEquals("cantidad", ex.getCampo());
        assertTrue(ex.getMessage().contains("100"));
        assertEquals(100, producto.getStock());
        verify(movimientos, never()).save(any(Movimiento.class));
    }

    @Test
    @DisplayName("Rechaza una cantidad que no sea positiva, que invertiria el efecto del movimiento")
    void cantidadNoPositiva() {
        // Una cantidad negativa en una entrada descontaria stock registrandolo
        // como ingreso, y en una salida lo sumaria sin validar disponibilidad.
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarEntrada(solicitud(-5, "COMPRA", null), 7L));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarSalida(solicitud(0, "ALQUILER", null), 7L));

        assertEquals(100, producto.getStock());
    }

    @Test
    @DisplayName("Rechaza el movimiento sobre un producto dado de baja")
    void productoInactivo() {
        producto.setActivo(false);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarSalida(solicitud(1, "ALQUILER", null), 7L));

        assertEquals("productoId", ex.getCampo());
    }

    // --- HU-15: anulacion ---

    @Test
    @DisplayName("CP-16: anular una salida devuelve el stock y crea el asiento compensatorio")
    void anularSalida() {
        Movimiento original = asiento(TiposMovimiento.SALIDA, 40);
        producto.setStock(60);
        when(movimientos.findById(5L)).thenReturn(Optional.of(original));

        Movimiento compensatorio = servicio.anular(
                5L, new AnulacionRequest("Se cargo a la obra equivocada"), 7L);

        assertEquals(100, producto.getStock());
        assertEquals(EstadosMovimiento.ANULADO, original.getEstado());
        assertNotNull(original.getFechaAnulacion());
        assertEquals(usuario, original.getUsuarioAnulacion());

        assertEquals(TiposMovimiento.ENTRADA, compensatorio.getTipo());
        assertEquals(EstadosMovimiento.COMPENSACION, compensatorio.getEstado());
        assertEquals(40, compensatorio.getCantidad());
        assertEquals(original, compensatorio.getMovimientoOrigen());
        assertEquals(100, compensatorio.getSaldoResultante());
    }

    @Test
    @DisplayName("CP-16: el asiento original y el compensatorio se cancelan entre si")
    void elParSeCancela() {
        Movimiento original = asiento(TiposMovimiento.SALIDA, 40);
        producto.setStock(60);
        when(movimientos.findById(5L)).thenReturn(Optional.of(original));

        Movimiento compensatorio = servicio.anular(
                5L, new AnulacionRequest("Error de digitacion"), 7L);

        // Las dos filas quedan en el kardex: el anulado sigue contando y lo
        // revierte el compensatorio, de modo que la suma es cero.
        assertEquals(0, original.efectoEnStock() + compensatorio.efectoEnStock());
    }

    @Test
    @DisplayName("CP-16: no se puede anular dos veces el mismo movimiento")
    void anularDosVeces() {
        Movimiento original = asiento(TiposMovimiento.SALIDA, 40);
        original.setEstado(EstadosMovimiento.ANULADO);
        when(movimientos.findById(5L)).thenReturn(Optional.of(original));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.anular(5L, new AnulacionRequest("Otra vez, por error"), 7L));

        assertTrue(ex.getMessage().contains("ya fue anulado"));
    }

    @Test
    @DisplayName("CP-16: no se anula un asiento de compensacion")
    void noSeAnulaUnaCompensacion() {
        Movimiento compensatorio = asiento(TiposMovimiento.ENTRADA, 40);
        compensatorio.setEstado(EstadosMovimiento.COMPENSACION);
        when(movimientos.findById(5L)).thenReturn(Optional.of(compensatorio));

        assertThrows(ReglaNegocioException.class,
                () -> servicio.anular(5L, new AnulacionRequest("No corresponde"), 7L));
    }

    @Test
    @DisplayName("CP-16: rechaza anular una entrada cuya mercaderia ya salio")
    void anularEntradaYaConsumida() {
        // Entro 50 y despues salieron 70: anular la entrada dejaria el stock en
        // -20, asi que la correccion debe hacerse por ajuste de inventario.
        Movimiento entrada = asiento(TiposMovimiento.ENTRADA, 50);
        producto.setStock(30);
        when(movimientos.findById(5L)).thenReturn(Optional.of(entrada));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.anular(5L, new AnulacionRequest("Factura anulada"), 7L));

        assertTrue(ex.getMessage().contains("ajuste de inventario"));
        assertEquals(30, producto.getStock());
        assertEquals(EstadosMovimiento.REGISTRADO, entrada.getEstado());
    }

    private Movimiento asiento(String tipo, int cantidad) {
        Movimiento m = new Movimiento();
        m.setId(5L);
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setEstado(EstadosMovimiento.REGISTRADO);
        m.setCostoUnitario(new BigDecimal("300.0000"));
        m.setProducto(producto);
        m.setUsuario(usuario);
        return m;
    }

    // --- HU-17: promedio ponderado, calculo puro ---

    @Test
    @DisplayName("HU-17: el promedio ponderado pesa por unidades, no por numero de compras")
    void promedioPonderado() {
        Producto p = new Producto();
        p.setStock(10);
        p.setCostoPromedio(new BigDecimal("100"));

        // 10 a 100 mas 90 a 200 = (1000 + 18000) / 100 = 190, no 150
        BigDecimal promedio = MovimientoService.promedioPonderado(p, 90, new BigDecimal("200"));

        assertEquals(0, new BigDecimal("190.0000").compareTo(promedio));
    }

    @Test
    @DisplayName("HU-17: con stock en cero la entrada fija el costo")
    void promedioConStockCero() {
        Producto p = new Producto();
        p.setStock(0);
        p.setCostoPromedio(new BigDecimal("999"));

        BigDecimal promedio = MovimientoService.promedioPonderado(p, 20, new BigDecimal("150"));

        assertEquals(0, new BigDecimal("150.0000").compareTo(promedio));
    }

    @Test
    @DisplayName("HU-17: redondea a cuatro decimales, igual que la columna de la base")
    void promedioRedondea() {
        Producto p = new Producto();
        p.setStock(3);
        p.setCostoPromedio(new BigDecimal("10"));

        // (30 + 20) / 7 = 7.142857...
        BigDecimal promedio = MovimientoService.promedioPonderado(p, 4, new BigDecimal("5"));

        assertEquals(new BigDecimal("7.1429"), promedio);
    }
}

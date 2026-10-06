package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.AjusteRequest;
import com.proyecto.microservicio.dto.MovimientoRequest;
import com.proyecto.microservicio.dto.RechazoRequest;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.AjusteInventario;
import com.proyecto.microservicio.model.EstadosAjuste;
import com.proyecto.microservicio.model.MotivoMovimiento;
import com.proyecto.microservicio.model.Movimiento;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AjusteInventarioRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CP-17: ajuste de inventario por conteo físico con aprobación.
 *
 * Las pruebas cubren el control que da sentido a la historia: el conteo por sí
 * solo no modifica el inventario, y la aprobación no aplica una diferencia que
 * ya quedó vencida.
 */
class AjusteInventarioServiceTest {

    private AjusteInventarioRepository ajustes;
    private ProductoRepository productos;
    private UsuarioRepository usuarios;
    private MovimientoService movimientos;
    private AjusteInventarioService servicio;

    private Producto producto;
    private Usuario encargado;
    private Usuario gerente;

    @BeforeEach
    void preparar() {
        ajustes = mock(AjusteInventarioRepository.class);
        productos = mock(ProductoRepository.class);
        usuarios = mock(UsuarioRepository.class);
        movimientos = mock(MovimientoService.class);
        servicio = new AjusteInventarioService(ajustes, productos, usuarios, movimientos,
                mock(AuditoriaService.class));

        producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Rueda de nylon");
        producto.setStock(100);
        producto.setActivo(true);

        encargado = usuario(7L, "Luis");
        gerente = usuario(2L, "Marta");

        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(usuarios.findById(7L)).thenReturn(Optional.of(encargado));
        when(usuarios.findById(2L)).thenReturn(Optional.of(gerente));
        when(ajustes.save(any(AjusteInventario.class))).thenAnswer(i -> {
            AjusteInventario a = i.getArgument(0);
            if (a.getId() == null) {
                a.setId(50L);
            }
            return a;
        });
    }

    private static Usuario usuario(Long id, String nombre) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombre(nombre);
        return u;
    }

    @Test
    @DisplayName("CP-17: el conteo queda pendiente y no toca el inventario")
    void elConteoNoModificaElStock() {
        AjusteInventario a = servicio.registrar(
                new AjusteRequest(1L, 95, "Faltan cinco en el estante"), 7L);

        assertEquals(EstadosAjuste.PENDIENTE, a.getEstado());
        assertEquals(100, a.getStockSistema());
        assertEquals(95, a.getStockFisico());
        assertEquals(-5, a.getDiferencia());
        assertEquals(encargado, a.getUsuarioSolicita());

        // Lo esencial: el stock sigue intacto hasta que alguien apruebe.
        assertEquals(100, producto.getStock());
        verify(productos, never()).save(any(Producto.class));
        verifyNoInteractions(movimientos);
    }

    @Test
    @DisplayName("CP-17: el stock de sistema lo lee el servidor, no lo informa el cliente")
    void elStockDeSistemaLoLeeElServidor() {
        // La solicitud solo trae lo contado. Si el cliente pudiera enviar el
        // stock de sistema, podria declarar una diferencia distinta de la real.
        AjusteInventario a = servicio.registrar(new AjusteRequest(1L, 120, null), 7L);

        assertEquals(100, a.getStockSistema());
        assertEquals(20, a.getDiferencia());
    }

    @Test
    @DisplayName("CP-17: rechaza un segundo conteo pendiente sobre el mismo producto")
    void noAdmiteDosConteosPendientes() {
        when(ajustes.tienePendiente(1L)).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(new AjusteRequest(1L, 95, null), 7L));

        assertEquals("productoId", ex.getCampo());
        verify(ajustes, never()).save(any(AjusteInventario.class));
    }

    @Test
    @DisplayName("CP-17: rechaza el conteo que coincide con el stock registrado")
    void conteoSinDiferencia() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(new AjusteRequest(1L, 100, null), 7L));

        assertEquals("stockFisico", ex.getCampo());
    }

    @Test
    @DisplayName("CP-17: aprobar una falta genera un movimiento de salida por la diferencia")
    void aprobarFaltaGeneraSalida() {
        AjusteInventario a = pendiente(-5);
        when(ajustes.findById(50L)).thenReturn(Optional.of(a));
        when(movimientos.registrarSalida(any(MovimientoRequest.class), anyLong()))
                .thenReturn(movimiento(200L, "AJUSTE_SALIDA"));

        AjusteInventario aprobado = servicio.aprobar(50L, 2L);

        ArgumentCaptor<MovimientoRequest> captor =
                ArgumentCaptor.forClass(MovimientoRequest.class);
        verify(movimientos).registrarSalida(captor.capture(), eq(2L));

        assertEquals(1L, captor.getValue().productoId());
        assertEquals(5, captor.getValue().cantidad());
        assertEquals("AJUSTE_SALIDA", captor.getValue().motivo());

        assertEquals(EstadosAjuste.APROBADO, aprobado.getEstado());
        assertEquals(gerente, aprobado.getUsuarioAprueba());
        assertEquals(200L, aprobado.getMovimiento().getId());
        assertNotNull(aprobado.getFechaResolucion());
    }

    @Test
    @DisplayName("CP-17: aprobar un sobrante genera un movimiento de entrada")
    void aprobarSobranteGeneraEntrada() {
        AjusteInventario a = pendiente(8);
        when(ajustes.findById(50L)).thenReturn(Optional.of(a));
        when(movimientos.registrarEntrada(any(MovimientoRequest.class), anyLong()))
                .thenReturn(movimiento(201L, "AJUSTE_ENTRADA"));

        servicio.aprobar(50L, 2L);

        ArgumentCaptor<MovimientoRequest> captor =
                ArgumentCaptor.forClass(MovimientoRequest.class);
        verify(movimientos).registrarEntrada(captor.capture(), eq(2L));

        assertEquals(8, captor.getValue().cantidad());
        assertEquals("AJUSTE_ENTRADA", captor.getValue().motivo());
        verify(movimientos, never()).registrarSalida(any(), anyLong());
    }

    @Test
    @DisplayName("CP-17: no aplica una diferencia vencida si el stock cambio desde el conteo")
    void rechazaDiferenciaVencida() {
        // El conteo se registro contra 100 unidades, pero despues hubo una
        // salida y ahora hay 80. Aplicar -5 sobre 80 daria 75, cuando lo
        // contado fueron 95: la diferencia ya no corresponde.
        AjusteInventario a = pendiente(-5);
        when(ajustes.findById(50L)).thenReturn(Optional.of(a));
        producto.setStock(80);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.aprobar(50L, 2L));

        assertTrue(ex.getMessage().contains("recuento"));
        assertEquals(EstadosAjuste.PENDIENTE, a.getEstado());
        verifyNoInteractions(movimientos);
    }

    @Test
    @DisplayName("CP-17: un ajuste ya resuelto no se vuelve a resolver")
    void ajusteYaResuelto() {
        AjusteInventario a = pendiente(-5);
        a.setEstado(EstadosAjuste.APROBADO);
        when(ajustes.findById(50L)).thenReturn(Optional.of(a));

        assertThrows(ReglaNegocioException.class, () -> servicio.aprobar(50L, 2L));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.rechazar(50L, new RechazoRequest("Ya estaba aprobado"), 2L));
    }

    @Test
    @DisplayName("CP-17: el rechazo deja el motivo y no genera ningun movimiento")
    void rechazo() {
        AjusteInventario a = pendiente(-5);
        when(ajustes.findById(50L)).thenReturn(Optional.of(a));

        AjusteInventario rechazado = servicio.rechazar(
                50L, new RechazoRequest("El conteo no incluyo el deposito dos"), 2L);

        assertEquals(EstadosAjuste.RECHAZADO, rechazado.getEstado());
        assertEquals("El conteo no incluyo el deposito dos", rechazado.getMotivoRechazo());
        assertEquals(gerente, rechazado.getUsuarioAprueba());
        assertEquals(100, producto.getStock());
        verifyNoInteractions(movimientos);
    }

    private AjusteInventario pendiente(int diferencia) {
        AjusteInventario a = new AjusteInventario();
        a.setId(50L);
        a.setProducto(producto);
        a.setStockSistema(100);
        a.setStockFisico(100 + diferencia);
        a.setDiferencia(diferencia);
        a.setEstado(EstadosAjuste.PENDIENTE);
        a.setUsuarioSolicita(encargado);
        return a;
    }

    private Movimiento movimiento(Long id, String codigoMotivo) {
        MotivoMovimiento motivo = new MotivoMovimiento();
        motivo.setCodigo(codigoMotivo);
        Movimiento m = new Movimiento();
        m.setId(id);
        m.setMotivo(motivo);
        m.setProducto(producto);
        return m;
    }
}

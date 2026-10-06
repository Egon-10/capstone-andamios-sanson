package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;

import com.proyecto.microservicio.dto.AnulacionRequest;
import com.proyecto.microservicio.dto.MovimientoRequest;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.EstadosMovimiento;
import com.proyecto.microservicio.model.MotivoMovimiento;
import com.proyecto.microservicio.model.Movimiento;
import com.proyecto.microservicio.model.MovimientoDTO;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.TiposMovimiento;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registro de entradas y salidas de inventario.
 *
 * Tres decisiones de diseño gobiernan esta clase:
 *
 * 1. Quien registra el movimiento llega como parámetro desde el token de la
 *    sesión y nunca desde la solicitud (CR-04). El método no acepta otra vía.
 *
 * 2. Cada operación es transaccional (HU-12): la actualización del stock y el
 *    asiento del kardex se confirman juntos o no se confirman. Antes podían
 *    quedar desalineados si fallaba el segundo guardado.
 *
 * 3. El stock se protege con bloqueo optimista (HU-13). La columna version del
 *    producto hace que dos salidas simultáneas del mismo producto no puedan
 *    dejar el inventario en negativo: la segunda escritura falla y se informa
 *    al cliente para que reintente sobre el stock ya actualizado.
 *
 * Un movimiento es inmutable: no se edita ni se borra. Para corregirlo se
 * anula, lo que genera un asiento compensatorio (HU-15).
 */
@Service
public class MovimientoService {

    /** Escala del costo promedio ponderado, igual que la columna de la base. */
    private static final int ESCALA_COSTO = 4;

    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MotivoMovimientoService motivos;
    private final AuditoriaService auditoria;

    public MovimientoService(MovimientoRepository movimientoRepository,
                             ProductoRepository productoRepository,
                             UsuarioRepository usuarioRepository,
                             MotivoMovimientoService motivos,
                             AuditoriaService auditoria) {
        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.motivos = motivos;
        this.auditoria = auditoria;
    }

    public List<MovimientoDTO> listarMovimientos() {
        return movimientoRepository.obtenerMovimientosConDetalle();
    }

    public List<MovimientoDTO> filtrarMovimientos(LocalDateTime fechaInicio,
                                                  LocalDateTime fechaFin,
                                                  String tipo) {
        return movimientoRepository.buscarConFiltros(fechaInicio, fechaFin, tipo);
    }

    public Movimiento obtener(Long id) {
        return movimientoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Movimiento no encontrado"));
    }

    /** HU-12: ingreso de mercadería. Recalcula el costo promedio ponderado. */
    @Transactional
    public Movimiento registrarEntrada(MovimientoRequest s, Long usuarioId) {
        return registrar(TiposMovimiento.ENTRADA, s, usuarioId);
    }

    /** HU-13: salida de mercadería, con validación de disponibilidad. */
    @Transactional
    public Movimiento registrarSalida(MovimientoRequest s, Long usuarioId) {
        return registrar(TiposMovimiento.SALIDA, s, usuarioId);
    }

    private Movimiento registrar(String tipo, MovimientoRequest s, Long usuarioId) {
        // La anotación @Positive del DTO ya rechaza las cantidades no positivas,
        // pero la regla se repite aquí porque el servicio también se invoca desde
        // la aprobación de ajustes (HU-16), que no pasa por la validación del DTO.
        if (s.cantidad() == null || s.cantidad() <= 0) {
            throw new ReglaNegocioException("cantidad", "La cantidad debe ser mayor que cero");
        }

        MotivoMovimiento motivo = motivos.validar(s.motivo(), tipo, s.observacion());
        Producto producto = productoActivo(s.productoId());
        Usuario usuario = usuario(usuarioId);

        BigDecimal costoUnitario = TiposMovimiento.ENTRADA.equals(tipo)
                ? costoDeEntrada(producto, s.costoUnitario())
                : costoVigente(producto);

        if (TiposMovimiento.ENTRADA.equals(tipo)) {
            producto.setCostoPromedio(
                    promedioPonderado(producto, s.cantidad(), costoUnitario));
        } else {
            verificarDisponibilidad(producto, s.cantidad());
        }

        int stockResultante = stockActual(producto) + TiposMovimiento.signo(tipo) * s.cantidad();
        producto.setStock(stockResultante);
        productoRepository.save(producto);

        Movimiento movimiento = new Movimiento();
        movimiento.setTipo(tipo);
        movimiento.setMotivo(motivo);
        movimiento.setObservacion(limpiar(s.observacion()));
        movimiento.setCantidad(s.cantidad());
        movimiento.setFecha(ZonaHoraria.ahora());
        movimiento.setEstado(EstadosMovimiento.REGISTRADO);
        movimiento.setCostoUnitario(costoUnitario);
        movimiento.setSaldoResultante(stockResultante);
        movimiento.setProducto(producto);
        movimiento.setUsuario(usuario);

        Movimiento guardado = movimientoRepository.save(movimiento);

        auditoria.registrar("MOVIMIENTO_" + tipo,
                "Movimiento " + guardado.getId() + ": " + tipo + " de " + s.cantidad()
                        + " unidades de " + producto.getNombre() + " (" + motivo.getCodigo()
                        + "). Saldo resultante: " + stockResultante,
                usuarioId);

        return guardado;
    }

    /**
     * HU-15: anula un movimiento mediante un asiento compensatorio.
     *
     * No se borra ni se modifica el asiento original. Se lo marca como anulado
     * y se registra un segundo asiento de tipo contrario, de modo que el
     * kardex conserva las dos filas y la suma de ambas sobre el stock es cero.
     */
    @Transactional
    public Movimiento anular(Long id, AnulacionRequest s, Long usuarioId) {
        Movimiento original = obtener(id);

        if (!EstadosMovimiento.sePuedeAnular(original.getEstado())) {
            throw new ReglaNegocioException(
                    EstadosMovimiento.ANULADO.equals(original.getEstado())
                            ? "El movimiento ya fue anulado"
                            : "Un asiento de compensación no se puede anular");
        }

        Producto producto = original.getProducto();
        if (producto == null) {
            throw new ReglaNegocioException("El movimiento no tiene producto asociado");
        }

        String tipoCompensatorio = TiposMovimiento.contrario(original.getTipo());
        int efecto = TiposMovimiento.signo(tipoCompensatorio) * original.getCantidad();
        int stockResultante = stockActual(producto) + efecto;

        // Anular una entrada descuenta del stock. Si esa mercadería ya salió, el
        // descuento dejaría el inventario en negativo: la anulación no procede y
        // la corrección debe hacerse por ajuste de inventario (HU-16).
        if (stockResultante < 0) {
            throw new ReglaNegocioException(
                    "No se puede anular: el stock de " + producto.getNombre() + " es "
                            + stockActual(producto) + " y la anulación exige descontar "
                            + original.getCantidad() + " unidades. Registre un ajuste de inventario.");
        }

        Usuario usuario = usuario(usuarioId);
        LocalDateTime ahora = ZonaHoraria.ahora();

        producto.setStock(stockResultante);
        productoRepository.save(producto);

        original.setEstado(EstadosMovimiento.ANULADO);
        original.setFechaAnulacion(ahora);
        original.setUsuarioAnulacion(usuario);
        movimientoRepository.save(original);

        Movimiento compensatorio = new Movimiento();
        compensatorio.setTipo(tipoCompensatorio);
        compensatorio.setMotivo(motivos.motivoDeAnulacion(tipoCompensatorio));
        compensatorio.setObservacion(
                "Anulación del movimiento " + original.getId() + ": " + s.justificacion().trim());
        compensatorio.setCantidad(original.getCantidad());
        compensatorio.setFecha(ahora);
        compensatorio.setEstado(EstadosMovimiento.COMPENSACION);
        compensatorio.setCostoUnitario(original.getCostoUnitario());
        compensatorio.setSaldoResultante(stockResultante);
        compensatorio.setMovimientoOrigen(original);
        compensatorio.setProducto(producto);
        compensatorio.setUsuario(usuario);

        Movimiento guardado = movimientoRepository.save(compensatorio);

        auditoria.registrar("MOVIMIENTO_ANULADO",
                "Se anuló el movimiento " + original.getId() + " (" + original.getTipo() + " de "
                        + original.getCantidad() + " unidades de " + producto.getNombre()
                        + ") con el asiento compensatorio " + guardado.getId()
                        + ". Justificación: " + s.justificacion().trim(),
                usuarioId);

        return guardado;
    }

    /**
     * Costo promedio ponderado tras una entrada (HU-17):
     *
     *   (stock * promedio + cantidad * costoEntrada) / (stock + cantidad)
     *
     * Es el método de valorización acordado con la empresa. La salida no altera
     * el promedio: se valoriza al promedio vigente y lo deja intacto.
     */
    static BigDecimal promedioPonderado(Producto producto, int cantidad, BigDecimal costoEntrada) {
        int stock = producto.getStock() == null ? 0 : producto.getStock();
        BigDecimal promedio = producto.getCostoPromedio() == null
                ? BigDecimal.ZERO
                : producto.getCostoPromedio();

        int unidadesTotales = stock + cantidad;
        if (unidadesTotales <= 0) {
            return promedio;
        }

        // Un stock negativo heredado no debe arrastrarse al cálculo: en ese caso
        // la entrada fija el costo, que es el único dato confiable.
        if (stock <= 0) {
            return costoEntrada.setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        }

        BigDecimal valorExistente = promedio.multiply(BigDecimal.valueOf(stock));
        BigDecimal valorEntrante = costoEntrada.multiply(BigDecimal.valueOf(cantidad));

        return valorExistente.add(valorEntrante)
                .divide(BigDecimal.valueOf(unidadesTotales), ESCALA_COSTO, RoundingMode.HALF_UP);
    }

    private BigDecimal costoDeEntrada(Producto producto, BigDecimal informado) {
        // Una compra trae su propio costo. Una devolución o un traslado reingresan
        // al costo con el que el producto ya está valorizado.
        return informado != null ? informado : costoVigente(producto);
    }

    private static BigDecimal costoVigente(Producto producto) {
        return producto.getCostoPromedio() == null ? BigDecimal.ZERO : producto.getCostoPromedio();
    }

    private static void verificarDisponibilidad(Producto producto, int cantidad) {
        int stock = stockActual(producto);
        if (stock < cantidad) {
            throw new ReglaNegocioException("cantidad",
                    "Stock insuficiente: hay " + stock + " unidades de " + producto.getNombre()
                            + " y se solicitan " + cantidad);
        }
    }

    private static int stockActual(Producto producto) {
        return producto.getStock() == null ? 0 : producto.getStock();
    }

    private Producto productoActivo(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado"));
        if (!producto.isActivo()) {
            throw new ReglaNegocioException("productoId",
                    "El producto " + producto.getNombre() + " está dado de baja");
        }
        return producto;
    }

    private Usuario usuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}

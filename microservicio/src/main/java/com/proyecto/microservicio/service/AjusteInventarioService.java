package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;

import com.proyecto.microservicio.dto.AjusteRequest;
import com.proyecto.microservicio.dto.MovimientoRequest;
import com.proyecto.microservicio.dto.RechazoRequest;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.AjusteInventario;
import com.proyecto.microservicio.model.EstadosAjuste;
import com.proyecto.microservicio.model.Movimiento;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AjusteInventarioRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * HU-16: ajuste de inventario por conteo físico con aprobación.
 *
 * El flujo separa a quien cuenta de quien autoriza, que es el control que da
 * sentido a la historia. El encargado registra lo que contó y el ajuste queda
 * pendiente: el stock no cambia todavía. El administrador o el gerente lo
 * aprueban, y es la aprobación la que genera el movimiento que corrige el
 * inventario. Así una diferencia de conteo nunca modifica el stock sin que
 * quede constancia de quién la autorizó.
 *
 * Dos reglas evitan que el mecanismo se vuelva contra el inventario:
 *
 * - Un producto no puede tener dos conteos pendientes a la vez. Ambos se
 *   habrían calculado contra el mismo stock de sistema, y al aprobarse
 *   sumarían dos correcciones.
 *
 * - Al aprobar se vuelve a leer el stock. Si cambió desde el conteo, la
 *   diferencia registrada ya no corresponde a la realidad y el ajuste se
 *   rechaza pidiendo un recuento, en lugar de aplicar una corrección vencida.
 */
@Service
public class AjusteInventarioService {

    private final AjusteInventarioRepository repository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoService movimientos;
    private final AuditoriaService auditoria;

    public AjusteInventarioService(AjusteInventarioRepository repository,
                                   ProductoRepository productoRepository,
                                   UsuarioRepository usuarioRepository,
                                   MovimientoService movimientos,
                                   AuditoriaService auditoria) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientos = movimientos;
        this.auditoria = auditoria;
    }

    public List<AjusteInventario> listar() {
        return repository.findAllByOrderByFechaSolicitudDesc();
    }

    public List<AjusteInventario> listarPendientes() {
        return repository.findByEstadoOrderByFechaSolicitudAsc(EstadosAjuste.PENDIENTE);
    }

    public List<AjusteInventario> listarPorProducto(Long productoId) {
        return repository.findByProductoIdOrderByFechaSolicitudDesc(productoId);
    }

    public AjusteInventario obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ajuste no encontrado"));
    }

    /** Registra el conteo físico. No modifica el stock: deja el ajuste pendiente. */
    @Transactional
    public AjusteInventario registrar(AjusteRequest s, Long usuarioId) {
        Producto producto = producto(s.productoId());
        Usuario usuario = usuario(usuarioId);

        if (repository.tienePendiente(producto.getId())) {
            throw new ReglaNegocioException("productoId",
                    "El producto " + producto.getNombre() + " ya tiene un conteo pendiente de "
                            + "aprobación. Resuélvalo antes de registrar otro.");
        }

        int stockSistema = producto.getStock() == null ? 0 : producto.getStock();
        int diferencia = s.stockFisico() - stockSistema;

        if (diferencia == 0) {
            throw new ReglaNegocioException("stockFisico",
                    "El conteo coincide con el stock registrado (" + stockSistema
                            + " unidades). No hay nada que ajustar.");
        }

        AjusteInventario ajuste = new AjusteInventario();
        ajuste.setProducto(producto);
        ajuste.setStockSistema(stockSistema);
        ajuste.setStockFisico(s.stockFisico());
        ajuste.setDiferencia(diferencia);
        ajuste.setObservacion(limpiar(s.observacion()));
        ajuste.setEstado(EstadosAjuste.PENDIENTE);
        ajuste.setUsuarioSolicita(usuario);
        ajuste.setFechaSolicitud(ZonaHoraria.ahora());

        AjusteInventario guardado = repository.save(ajuste);

        auditoria.registrar("AJUSTE_REGISTRADO",
                "Conteo fisico " + guardado.getId() + " de " + producto.getNombre()
                        + ": sistema " + stockSistema + ", contado " + s.stockFisico()
                        + ", diferencia " + con_signo(diferencia) + ". Pendiente de aprobacion.",
                usuarioId);

        return guardado;
    }

    /**
     * Aprueba el conteo y corrige el inventario.
     *
     * La corrección se aplica como un movimiento normal, no como una
     * escritura directa del stock, para que la diferencia quede en el kardex
     * con su motivo y su responsable como cualquier otra entrada o salida.
     */
    @Transactional
    public AjusteInventario aprobar(Long id, Long usuarioId) {
        AjusteInventario ajuste = pendiente(id);
        Producto producto = ajuste.getProducto();
        Usuario aprobador = usuario(usuarioId);

        int stockActual = producto.getStock() == null ? 0 : producto.getStock();
        if (stockActual != ajuste.getStockSistema()) {
            throw new ReglaNegocioException(
                    "El stock de " + producto.getNombre() + " cambio desde el conteo: era "
                            + ajuste.getStockSistema() + " y ahora es " + stockActual
                            + ". La diferencia registrada ya no corresponde. "
                            + "Rechace este ajuste y registre un recuento.");
        }

        boolean sobra = ajuste.getDiferencia() > 0;
        int cantidad = Math.abs(ajuste.getDiferencia());

        String detalle = "Ajuste por conteo fisico " + ajuste.getId()
                + ": sistema " + ajuste.getStockSistema()
                + ", contado " + ajuste.getStockFisico()
                + (ajuste.getObservacion() == null ? "" : ". " + ajuste.getObservacion());

        MovimientoRequest solicitud = new MovimientoRequest(
                producto.getId(),
                cantidad,
                sobra ? "AJUSTE_ENTRADA" : "AJUSTE_SALIDA",
                detalle,
                null);

        Movimiento movimiento = sobra
                ? movimientos.registrarEntrada(solicitud, usuarioId)
                : movimientos.registrarSalida(solicitud, usuarioId);

        ajuste.setEstado(EstadosAjuste.APROBADO);
        ajuste.setUsuarioAprueba(aprobador);
        ajuste.setFechaResolucion(ZonaHoraria.ahora());
        ajuste.setMovimiento(movimiento);
        ajuste.setMotivo(movimiento.getMotivo());

        AjusteInventario guardado = repository.save(ajuste);

        auditoria.registrar("AJUSTE_APROBADO",
                "Se aprobo el conteo " + ajuste.getId() + " de " + producto.getNombre()
                        + ". Diferencia " + con_signo(ajuste.getDiferencia())
                        + " aplicada con el movimiento " + movimiento.getId()
                        + ". Solicitado por " + nombre(ajuste.getUsuarioSolicita()) + ".",
                usuarioId);

        return guardado;
    }

    /** Rechaza el conteo. El inventario no se toca y queda el motivo del rechazo. */
    @Transactional
    public AjusteInventario rechazar(Long id, RechazoRequest s, Long usuarioId) {
        AjusteInventario ajuste = pendiente(id);
        Usuario aprobador = usuario(usuarioId);

        ajuste.setEstado(EstadosAjuste.RECHAZADO);
        ajuste.setUsuarioAprueba(aprobador);
        ajuste.setFechaResolucion(ZonaHoraria.ahora());
        ajuste.setMotivoRechazo(s.motivo().trim());

        AjusteInventario guardado = repository.save(ajuste);

        auditoria.registrar("AJUSTE_RECHAZADO",
                "Se rechazo el conteo " + ajuste.getId() + " de "
                        + (ajuste.getProducto() == null ? "?" : ajuste.getProducto().getNombre())
                        + ". Motivo: " + s.motivo().trim(),
                usuarioId);

        return guardado;
    }

    private AjusteInventario pendiente(Long id) {
        AjusteInventario ajuste = obtener(id);
        if (!ajuste.estaPendiente()) {
            throw new ReglaNegocioException(
                    "El ajuste ya fue " + ajuste.getEstado().toLowerCase(java.util.Locale.ROOT)
                            + " y no se puede volver a resolver");
        }
        return ajuste;
    }

    private Producto producto(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado"));
        if (!producto.isActivo()) {
            throw new ReglaNegocioException("productoId",
                    "El producto " + producto.getNombre() + " esta dado de baja");
        }
        return producto;
    }

    private Usuario usuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private static String nombre(Usuario u) {
        return u == null ? "?" : u.getNombre();
    }

    static String con_signo(int valor) {
        return valor > 0 ? "+" + valor : String.valueOf(valor);
    }

    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}

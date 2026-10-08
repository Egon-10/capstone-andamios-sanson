package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.dto.KardexResponse;
import com.proyecto.microservicio.dto.StockCriticoResponse;
import com.proyecto.microservicio.dto.ValorizacionResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.model.EstadosMovimiento;
import com.proyecto.microservicio.model.MovimientoDTO;
import com.proyecto.microservicio.model.ReporteProductoDTO;
import com.proyecto.microservicio.model.TiposMovimiento;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.reporte.Reporte;
import com.proyecto.microservicio.reporte.Valores;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.RolRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * HU-26, HU-27 y HU-28: arma el contenido de cada reporte.
 *
 * Esta clase decide qué se muestra; {@code RenderizadorPdf} y
 * {@code RenderizadorExcel} deciden cómo. Los datos salen de los mismos
 * servicios que alimentan las pantallas (valorización, kardex, stock crítico,
 * bitácoras), de modo que un reporte nunca contradice lo que el usuario ve.
 */
@Service
public class ReporteService {

    /** Tope de filas por reporte: protege al servidor de una exportación sin filtros. */
    public static final int MAXIMO_FILAS = 5000;

    private final MovimientoRepository movimientos;
    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final ValorizacionService valorizacion;
    private final KardexService kardex;
    private final IndicadoresService indicadores;
    private final AuditoriaService auditoria;
    private final AccesoService accesos;
    private final UsuarioService usuarioService;

    public ReporteService(MovimientoRepository movimientos, ProductoRepository productos,
                          CategoriaRepository categorias, UsuarioRepository usuarios, RolRepository roles,
                          ValorizacionService valorizacion, KardexService kardex,
                          IndicadoresService indicadores, AuditoriaService auditoria,
                          AccesoService accesos, UsuarioService usuarioService) {
        this.movimientos = movimientos;
        this.productos = productos;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.roles = roles;
        this.valorizacion = valorizacion;
        this.kardex = kardex;
        this.indicadores = indicadores;
        this.auditoria = auditoria;
        this.accesos = accesos;
        this.usuarioService = usuarioService;
    }

    // ------------------------------------------------------------------
    // Inventario
    // ------------------------------------------------------------------

    /** Movimientos de un periodo, con totales de lo registrado (no anulado). */
    @Transactional(readOnly = true)
    public Reporte movimientos(String tipo, LocalDate desde, LocalDate hasta, Long emisorId) {
        validarRango(desde, hasta);
        String filtroTipo = tipoMovimiento(tipo);
        List<MovimientoDTO> filas = movimientos.buscarConFiltros(
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.atTime(LocalTime.MAX),
                filtroTipo);

        List<String> notas = new ArrayList<>();
        List<MovimientoDTO> visibles = recortar(filas, notas);

        int unidadesEntrada = 0;
        int unidadesSalida = 0;
        BigDecimal importe = BigDecimal.ZERO;
        List<List<Object>> datos = new ArrayList<>(visibles.size());
        for (MovimientoDTO m : visibles) {
            BigDecimal costo = m.getCostoUnitario() == null ? BigDecimal.ZERO : m.getCostoUnitario();
            int cantidad = m.getCantidad() == null ? 0 : m.getCantidad();
            BigDecimal valor = costo.multiply(BigDecimal.valueOf(cantidad));
            if (EstadosMovimiento.REGISTRADO.equals(m.getEstado())) {
                if (TiposMovimiento.ENTRADA.equals(m.getTipo())) {
                    unidadesEntrada += cantidad;
                } else {
                    unidadesSalida += cantidad;
                }
                importe = importe.add(valor);
            }
            datos.add(fila(m.getFecha(), m.getTipo(), nombreMotivo(m), m.getSku(), m.getProducto(), cantidad,
                    costo, valor, m.getEstado(), m.getUsuario()));
        }

        notas.add("Unidades registradas: " + Valores.entero(unidadesEntrada) + " de entrada y "
                + Valores.entero(unidadesSalida) + " de salida.");
        notas.add("Los movimientos anulados y sus asientos de compensación se listan para conservar la "
                + "trazabilidad, pero no suman en los totales.");

        return new Reporte("Movimientos de inventario",
                List.of("Tipo: " + (filtroTipo == null ? "entradas y salidas" : filtroTipo.toLowerCase(Locale.ROOT)),
                        "Periodo: " + periodo(desde, hasta)),
                List.of(Reporte.fechaHora("Fecha"), Reporte.texto("Tipo", 9), Reporte.texto("Motivo", 16),
                        Reporte.texto("SKU", 10), Reporte.texto("Producto", 24), Reporte.entero("Cantidad"),
                        Reporte.decimal("Costo unit."), Reporte.moneda("Importe"), Reporte.texto("Estado", 12),
                        Reporte.texto("Usuario", 16)),
                datos,
                datos.isEmpty() ? null : fila("Total registrado", null, null, null, null, null, null, importe, null, null),
                notas, emisor(emisorId), ZonaHoraria.ahora());
    }

    /** Catálogo activo con stock, umbrales y costo. */
    @Transactional(readOnly = true)
    public Reporte productos(Long categoriaId, Long emisorId) {
        List<ReporteProductoDTO> filas = productos.obtenerParaReporte(categoriaId);
        List<String> notas = new ArrayList<>();
        List<ReporteProductoDTO> visibles = recortar(filas, notas);

        int unidades = 0;
        List<List<Object>> datos = new ArrayList<>(visibles.size());
        for (ReporteProductoDTO p : visibles) {
            unidades += p.getStock() == null ? 0 : p.getStock();
            datos.add(fila(p.getSku(), p.getNombre(), p.getCategoria(), p.getProveedor(), p.getStock(),
                    p.getStockMinimo(), p.getPuntoReposicion(), p.getStockMaximo(), p.getCostoPromedio(),
                    p.getPrecio() == null ? null : BigDecimal.valueOf(p.getPrecio())));
        }

        String categoria = categoriaId == null ? "todas"
                : categorias.findById(categoriaId).map(c -> c.getNombre()).orElse("(no existe)");
        return new Reporte("Catálogo de productos",
                List.of("Categoría: " + categoria, "Solo productos activos"),
                List.of(Reporte.texto("SKU", 10), Reporte.texto("Producto", 26), Reporte.texto("Categoría", 14),
                        Reporte.texto("Proveedor", 16), Reporte.entero("Stock"), Reporte.entero("Mínimo"),
                        Reporte.entero("Reposición"), Reporte.entero("Máximo"), Reporte.moneda("Costo prom."),
                        Reporte.moneda("Precio")),
                datos,
                datos.isEmpty() ? null : fila(datos.size() + " productos", null, null, null, unidades,
                        null, null, null, null, null),
                notas, emisor(emisorId), ZonaHoraria.ahora());
    }

    /** HU-25 impreso: productos agotados, críticos o bajos, con la cantidad a pedir. */
    @Transactional(readOnly = true)
    public Reporte stockCritico(Long emisorId) {
        List<StockCriticoResponse> filas = indicadores.stockCritico();
        List<List<Object>> datos = new ArrayList<>(filas.size());
        for (StockCriticoResponse s : filas) {
            datos.add(fila(nivel(s.nivel()), s.sku(), s.producto(), s.categoria(), s.stock(), s.umbral(),
                    s.faltanteHastaUmbral(), s.reponerHastaMaximo()));
        }
        return new Reporte("Productos en stock crítico",
                List.of("Productos activos que alcanzaron su punto de reposición"),
                List.of(Reporte.texto("Nivel", 9), Reporte.texto("SKU", 10), Reporte.texto("Producto", 26),
                        Reporte.texto("Categoría", 14), Reporte.entero("Stock"), Reporte.entero("Umbral"),
                        Reporte.entero("Faltante"), Reporte.entero("Pedir hasta máx.")),
                datos, null,
                List.of("Agotado: sin stock. Crítico: la mitad del umbral o menos. Bajo: en el umbral o por debajo.",
                        "\"Pedir hasta máx.\" solo aparece si el producto tiene definido un stock máximo."),
                emisor(emisorId), ZonaHoraria.ahora());
    }

    /** HU-28: valorización actual o con corte a una fecha. */
    @Transactional(readOnly = true)
    public Reporte valorizacion(LocalDate corte, Long emisorId) {
        ValorizacionResponse v = corte == null ? valorizacion.calcular() : valorizacion.calcularAlCorte(corte);
        boolean conCorte = v.fechaCorte() != null;

        List<List<Object>> datos = new ArrayList<>(v.detalle().size());
        for (ValorizacionResponse.PorProducto p : v.detalle()) {
            datos.add(fila(p.sku(), p.producto(), p.categoria(), p.stock(), p.costoPromedio(), p.valor(),
                    p.costoEstimado() ? "Estimado" : "Exacto"));
        }

        List<String> notas = new ArrayList<>();
        notas.add("Valorización al costo promedio ponderado. No se usa el precio de venta.");
        for (ValorizacionResponse.PorCategoria c : v.porCategoria()) {
            notas.add(c.categoria() + ": " + Valores.moneda(c.valor()) + " (" + Valores.decimal(c.participacion())
                    + " % del total, " + Valores.entero(c.unidades()) + " unidades).");
        }
        if (v.productosConCostoEstimado() != null && v.productosConCostoEstimado() > 0) {
            notas.add(v.productosConCostoEstimado() + " producto(s) con costo estimado: su último movimiento "
                    + "anterior al corte es previo al registro del costo por movimiento y se usó el costo vigente.");
        }

        return new Reporte(conCorte ? "Valorización del inventario al " + Valores.fecha(v.fechaCorte())
                        : "Valorización del inventario",
                List.of(conCorte ? "Corte: cierre del " + Valores.fecha(v.fechaCorte())
                        : "Corte: existencias vigentes al momento de la emisión"),
                List.of(Reporte.texto("SKU", 10), Reporte.texto("Producto", 28), Reporte.texto("Categoría", 16),
                        Reporte.entero("Stock"), Reporte.decimal("Costo prom."), Reporte.moneda("Valor"),
                        Reporte.texto("Costo", 9)),
                datos,
                datos.isEmpty() ? null : fila("Total", null, null, v.unidadesTotales(), null, v.valorTotal(), null),
                notas, emisor(emisorId), ZonaHoraria.ahora());
    }

    /** HU-18 impreso: kardex de un producto. */
    @Transactional(readOnly = true)
    public Reporte kardex(Long productoId, LocalDate desde, LocalDate hasta, Long emisorId) {
        validarRango(desde, hasta);
        KardexResponse k = kardex.obtener(productoId,
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.atTime(LocalTime.MAX));

        List<String> notas = new ArrayList<>();
        List<KardexResponse.Linea> visibles = recortar(k.lineas(), notas);
        List<List<Object>> datos = new ArrayList<>(visibles.size());
        for (KardexResponse.Linea l : visibles) {
            datos.add(fila(l.fecha(), l.motivoNombre() != null ? l.motivoNombre() : l.motivo(), l.entrada(),
                    l.salida(), l.costoUnitario(), l.valorizado(), l.saldo(), l.estado(), l.usuario()));
        }
        notas.add("Stock actual: " + Valores.entero(k.stockActual()) + " unidades, valorizadas en "
                + Valores.moneda(k.valorizado()) + ".");

        return new Reporte("Kardex de " + k.sku() + " - " + k.producto(),
                List.of("Periodo: " + periodo(desde, hasta),
                        "Saldo al inicio del periodo: " + Valores.entero(k.saldoInicial()) + " unidades"),
                List.of(Reporte.fechaHora("Fecha"), Reporte.texto("Motivo", 18), Reporte.entero("Entrada"),
                        Reporte.entero("Salida"), Reporte.decimal("Costo unit."), Reporte.moneda("Importe"),
                        Reporte.entero("Saldo"), Reporte.texto("Estado", 12), Reporte.texto("Usuario", 16)),
                datos, null, notas, emisor(emisorId), ZonaHoraria.ahora());
    }

    // ------------------------------------------------------------------
    // Administración
    // ------------------------------------------------------------------

    /** HU-33 impreso: bitácora de auditoría con los mismos filtros de la pantalla. */
    @Transactional(readOnly = true)
    public Reporte auditoria(FiltroBitacora filtro, Long emisorId) {
        Page<Auditoria> pagina = auditoria.buscarParaReporte(filtro, MAXIMO_FILAS);
        List<List<Object>> datos = new ArrayList<>(pagina.getNumberOfElements());
        for (Auditoria a : pagina.getContent()) {
            Usuario u = a.getUsuario();
            datos.add(fila(a.getFecha(), u == null ? "Sistema" : u.getNombreCompleto(), a.getAccion(),
                    a.getDetalle()));
        }
        return new Reporte("Bitácora de auditoría", filtrosBitacora(filtro),
                List.of(Reporte.fechaHora("Fecha"), Reporte.texto("Usuario", 18), Reporte.texto("Acción", 20),
                        Reporte.texto("Detalle", 60)),
                datos, null, notaTope(pagina.getTotalElements()), emisor(emisorId), ZonaHoraria.ahora());
    }

    /** HU-32 impreso: bitácora de accesos. */
    @Transactional(readOnly = true)
    public Reporte accesos(FiltroBitacora filtro, Long emisorId) {
        Page<Acceso> pagina = accesos.buscarParaReporte(filtro, MAXIMO_FILAS);
        List<List<Object>> datos = new ArrayList<>(pagina.getNumberOfElements());
        for (Acceso a : pagina.getContent()) {
            datos.add(fila(a.getFecha(), a.getIdentificador(), resultado(a.getResultado()), a.getMotivo(),
                    a.getIp()));
        }
        List<String> filtros = new ArrayList<>(filtrosBitacora(filtro));
        if (filtro.resultado() != null && !filtro.resultado().isBlank()) {
            filtros.add("Resultado: " + resultado(filtro.resultado().trim().toUpperCase(Locale.ROOT)));
        }
        return new Reporte("Bitácora de accesos", filtros,
                List.of(Reporte.fechaHora("Fecha"), Reporte.texto("Usuario escrito", 22),
                        Reporte.texto("Resultado", 10), Reporte.texto("Motivo", 22), Reporte.texto("IP", 16)),
                datos, null, notaTope(pagina.getTotalElements()), emisor(emisorId), ZonaHoraria.ahora());
    }

    /** HU-30 impreso: usuarios, sin documento ni datos de contacto. */
    @Transactional(readOnly = true)
    public Reporte usuarios(String texto, Long rolId, String estado, Long emisorId) {
        Page<Usuario> pagina = usuarioService.buscarParaReporte(texto, rolId, estado, MAXIMO_FILAS);
        List<List<Object>> datos = new ArrayList<>(pagina.getNumberOfElements());
        for (Usuario u : pagina.getContent()) {
            datos.add(fila(u.getNombreUsuario(), u.getNombreCompleto(), u.getRol() == null ? null : u.getRol().getNombre(),
                    u.getArea(), u.getTurno(), u.estaActivo() ? "Activo" : "Inactivo", u.getFechaCreacion()));
        }
        List<String> filtros = new ArrayList<>();
        filtros.add("Rol: " + (rolId == null ? "todos" : roles.findById(rolId).map(r -> r.getNombre()).orElse("(no existe)")));
        filtros.add("Estado: " + (estado == null || estado.isBlank() ? "todos" : estado.toLowerCase(Locale.ROOT)));
        if (texto != null && !texto.isBlank()) {
            filtros.add("Búsqueda: \"" + texto.trim() + "\"");
        }
        return new Reporte("Usuarios del sistema", filtros,
                List.of(Reporte.texto("Usuario", 14), Reporte.texto("Nombre", 26), Reporte.texto("Rol", 14),
                        Reporte.texto("Área", 12), Reporte.texto("Turno", 9), Reporte.texto("Estado", 9),
                        Reporte.fecha("Alta")),
                datos, null,
                List.of("Por protección de datos personales, el reporte no incluye documento, correo ni teléfono."),
                emisor(emisorId), ZonaHoraria.ahora());
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    private String emisor(Long emisorId) {
        return emisorId == null ? null : usuarios.findById(emisorId).map(Usuario::getNombreCompleto).orElse(null);
    }

    private static List<Object> fila(Object... valores) {
        return Arrays.asList(valores);
    }

    /** Acota la lista al tope y deja una nota si hubo que recortar. */
    private static <T> List<T> recortar(List<T> filas, List<String> notas) {
        if (filas.size() <= MAXIMO_FILAS) {
            return filas;
        }
        notas.add("Se muestran los primeros " + Valores.entero(MAXIMO_FILAS) + " de "
                + Valores.entero(filas.size()) + " registros. Acote los filtros para ver el resto.");
        return filas.subList(0, MAXIMO_FILAS);
    }

    private static List<String> notaTope(long total) {
        if (total <= MAXIMO_FILAS) {
            return List.of();
        }
        return List.of("Se muestran los " + Valores.entero(MAXIMO_FILAS) + " registros más recientes de "
                + Valores.entero(total) + ". Acote los filtros para ver el resto.");
    }

    private List<String> filtrosBitacora(FiltroBitacora f) {
        List<String> filtros = new ArrayList<>();
        filtros.add("Periodo: " + periodo(f.desde(), f.hasta()));
        if (f.usuarioId() != null) {
            filtros.add("Usuario: " + usuarios.findById(f.usuarioId()).map(Usuario::getNombreCompleto).orElse("(no existe)"));
        }
        if (f.texto() != null && !f.texto().isBlank()) {
            filtros.add("Búsqueda: \"" + f.texto().trim() + "\"");
        }
        return filtros;
    }

    static String periodo(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            return "todo el historial";
        }
        if (desde == null) {
            return "hasta el " + Valores.fecha(hasta);
        }
        if (hasta == null) {
            return "desde el " + Valores.fecha(desde);
        }
        return "del " + Valores.fecha(desde) + " al " + Valores.fecha(hasta);
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("desde", "La fecha inicial no puede ser posterior a la final");
        }
    }

    static String tipoMovimiento(String tipo) {
        if (tipo == null || tipo.isBlank() || "TODOS".equalsIgnoreCase(tipo.trim())) {
            return null;
        }
        String t = tipo.trim().toUpperCase(Locale.ROOT);
        if (!TiposMovimiento.ENTRADA.equals(t) && !TiposMovimiento.SALIDA.equals(t)) {
            throw new ReglaNegocioException("tipo", "El tipo debe ser ENTRADA o SALIDA");
        }
        return t;
    }

    private static String nombreMotivo(MovimientoDTO m) {
        return m.getMotivoNombre() != null ? m.getMotivoNombre() : m.getMotivo();
    }

    static String nivel(String nivel) {
        return switch (nivel == null ? "" : nivel) {
            case "AGOTADO" -> "Agotado";
            case "CRITICO" -> "Crítico";
            case "BAJO" -> "Bajo";
            default -> nivel;
        };
    }

    static String resultado(String resultado) {
        return switch (resultado == null ? "" : resultado) {
            case Acceso.EXITOSO -> "Exitoso";
            case Acceso.FALLIDO -> "Fallido";
            case Acceso.BLOQUEADO -> "Bloqueado";
            default -> resultado;
        };
    }
}

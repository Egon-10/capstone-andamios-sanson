package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.FichaProductoResponse;
import com.proyecto.microservicio.dto.FiltroProductos;
import com.proyecto.microservicio.dto.KardexResponse;
import com.proyecto.microservicio.dto.ProductoRequest;
import com.proyecto.microservicio.dto.UmbralesRequest;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.ProductoDTO;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.AjusteInventarioRepository;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProveedorRepository;
import com.proyecto.microservicio.model.ResumenProductoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** HU-10: registro de productos con validación en servidor y SKU único. */
@Service
public class ProductoService {

    /**
     * Campos por los que se admite ordenar (HU-21). La lista es cerrada porque
     * el nombre del campo llega por la URL: con cualquier valor, un campo
     * inexistente hace fallar la consulta con un error 500, y uno de una
     * relación permitiría ordenar por datos que la pantalla no muestra.
     */
    private static final Set<String> ORDENABLES =
            Set.of("nombre", "sku", "stock", "precio", "costoPromedio", "id");

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final ProductoRepository repository;
    private final CategoriaRepository categoriaRepository;
    private final ProveedorRepository proveedorRepository;
    private final MovimientoRepository movimientoRepository;
    private final AjusteInventarioRepository ajusteRepository;
    private final KardexService kardexService;

    public ProductoService(ProductoRepository repository, CategoriaRepository categoriaRepository,
                           ProveedorRepository proveedorRepository,
                           MovimientoRepository movimientoRepository,
                           AjusteInventarioRepository ajusteRepository,
                           KardexService kardexService) {
        this.repository = repository;
        this.categoriaRepository = categoriaRepository;
        this.proveedorRepository = proveedorRepository;
        this.movimientoRepository = movimientoRepository;
        this.ajusteRepository = ajusteRepository;
        this.kardexService = kardexService;
    }

    public List<Producto> listar() {
        return repository.findAll();
    }

    public List<ProductoDTO> listarDetalle() {
        return repository.obtenerProductosConDetalle();
    }

    public Producto obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado"));
    }

    @Transactional
    public Producto registrar(ProductoRequest s) {
        String sku = normalizarSku(s.sku());
        if (repository.existsBySkuIgnoreCase(sku)) {
            throw new ConflictoException("sku", "El SKU " + sku + " ya está registrado");
        }
        Producto p = new Producto();
        p.setStock(s.stock());
        aplicar(p, s, sku);
        return repository.save(p);
    }

    /** La edición no modifica el stock: el stock solo cambia mediante movimientos. */
    @Transactional
    public Producto actualizar(Long id, ProductoRequest s) {
        Producto p = obtener(id);
        String sku = normalizarSku(s.sku());
        if (repository.existsBySkuIgnoreCaseAndIdNot(sku, id)) {
            throw new ConflictoException("sku", "El SKU " + sku + " ya está registrado");
        }
        aplicar(p, s, sku);
        return repository.save(p);
    }

    public void eliminar(Long id) {
        repository.delete(obtener(id));
    }

    private void aplicar(Producto p, ProductoRequest s, String sku) {
        p.setSku(sku);
        p.setNombre(s.nombre().trim());
        p.setDescripcion(s.descripcion() == null ? null : s.descripcion().trim());
        p.setPrecio(s.precio());
        p.setStockMinimo(s.stockMinimo());
        com.proyecto.microservicio.model.Categoria categoria = categoriaRepository.findById(s.categoria().id())
                .orElseThrow(() -> new ReglaNegocioException("categoria", "La categoría seleccionada no existe"));
        // HU-11: una categoria dada de baja no se asigna a un producto nuevo ni
        // a uno que cambia de categoria. Si el producto ya la tenia, se respeta.
        if (!categoria.isActivo() && !esLaMisma(p.getCategoria(), categoria.getId())) {
            throw new ReglaNegocioException("categoria",
                    "La categoría " + categoria.getNombre() + " está dada de baja");
        }
        p.setCategoria(categoria);
        if (s.proveedor() != null && s.proveedor().id() != null) {
            com.proyecto.microservicio.model.Proveedor proveedor = proveedorRepository.findById(s.proveedor().id())
                    .orElseThrow(() -> new ReglaNegocioException("proveedor", "El proveedor seleccionado no existe"));
            if (!proveedor.isActivo()
                    && (p.getProveedor() == null || !proveedor.getId().equals(p.getProveedor().getId()))) {
                throw new ReglaNegocioException("proveedor",
                        "El proveedor " + proveedor.getNombre() + " está dado de baja");
            }
            p.setProveedor(proveedor);
        } else {
            p.setProveedor(null);
        }
    }

    /**
     * HU-21: búsqueda paginada en el servidor.
     *
     * La paginación y el filtrado se resuelven en la base. Antes el cliente
     * traía el catálogo completo y filtraba en memoria, lo que funcionaba
     * mientras el catálogo era pequeño y dejaba de funcionar justo cuando la
     * búsqueda empieza a hacer falta.
     */
    @Transactional(readOnly = true)
    public Page<Producto> buscar(FiltroProductos f) {
        String patron = f.texto() == null || f.texto().isBlank()
                ? null
                : "%" + f.texto().trim().toLowerCase(Locale.ROOT) + "%";

        return repository.buscar(patron, f.categoriaId(), f.proveedorId(), f.soloActivos(),
                f.porReponer(), paginacion(f.pagina(), f.tamano(), f.orden(), f.direccion()));
    }

    /**
     * Arma la paginación acotando los valores que llegan por la URL. Un tamaño
     * de página sin límite permitiría pedir el catálogo entero en una sola
     * consulta y anularía el sentido de paginar.
     */
    static Pageable paginacion(int pagina, int tamano, String orden, String direccion) {
        int paginaValida = Math.max(pagina, 0);
        int tamanoValido = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA);

        String campo = orden != null && ORDENABLES.contains(orden) ? orden : "nombre";
        Sort.Direction sentido = "desc".equalsIgnoreCase(direccion)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return PageRequest.of(paginaValida, tamanoValido, Sort.by(sentido, campo));
    }

    /**
     * HU-20: actualiza solo los umbrales de reposición. Se separa de la edición
     * general porque los define quien planifica las compras, y cambiarlos no
     * debe obligar a reenviar el resto de la ficha.
     */
    @Transactional
    public Producto actualizarUmbrales(Long id, UmbralesRequest s) {
        Producto p = obtener(id);

        Integer minimo = s.stockMinimo() != null ? s.stockMinimo() : p.getStockMinimo();
        Integer maximo = s.stockMaximo() != null ? s.stockMaximo() : p.getStockMaximo();
        Integer reposicion = s.puntoReposicion() != null ? s.puntoReposicion() : p.getPuntoReposicion();

        if (maximo != null && minimo != null && maximo < minimo) {
            throw new ReglaNegocioException("stockMaximo",
                    "El stock máximo (" + maximo + ") no puede ser menor que el mínimo ("
                            + minimo + ")");
        }
        if (reposicion != null && maximo != null && reposicion > maximo) {
            throw new ReglaNegocioException("puntoReposicion",
                    "El punto de reposición (" + reposicion + ") no puede superar el stock máximo ("
                            + maximo + ")");
        }

        p.setStockMinimo(minimo);
        p.setPuntoReposicion(reposicion);
        p.setStockMaximo(maximo);

        return repository.save(p);
    }

    /**
     * HU-22: ficha de detalle consolidada.
     *
     * Reúne en una respuesta lo que antes exigía cuatro llamadas desde el
     * cliente. Los últimos movimientos se toman del kardex para que el saldo
     * que muestra la ficha sea el mismo que muestra el kardex completo, en
     * lugar de calcularse por segunda vez con otra lógica.
     */
    @Transactional(readOnly = true)
    public FichaProductoResponse ficha(Long id, int ultimosMovimientos) {
        Producto p = obtener(id);
        ResumenProductoDTO r = movimientoRepository.obtenerResumen(id);
        KardexResponse kardex = kardexService.obtener(id, null, null);

        List<KardexResponse.Linea> lineas = kardex.lineas();
        int cuantos = Math.min(Math.max(ultimosMovimientos, 1), 50);
        if (lineas.size() > cuantos) {
            // Los ultimos son los del final: el kardex viene en orden cronologico.
            lineas = lineas.subList(lineas.size() - cuantos, lineas.size());
        }

        Integer faltante = p.getStockMaximo() == null || p.getStock() == null
                ? null
                : Math.max(p.getStockMaximo() - p.getStock(), 0);

        return new FichaProductoResponse(
                p.getId(),
                p.getSku(),
                p.getNombre(),
                p.getDescripcion(),
                p.isActivo(),
                p.getCategoria() == null ? null
                        : new FichaProductoResponse.Referencia(
                                p.getCategoria().getId(), p.getCategoria().getNombre()),
                p.getProveedor() == null ? null
                        : new FichaProductoResponse.Referencia(
                                p.getProveedor().getId(), p.getProveedor().getNombre()),
                new FichaProductoResponse.Existencias(
                        p.getStock(),
                        p.getStockMinimo(),
                        p.getPuntoReposicion(),
                        p.getStockMaximo(),
                        p.necesitaReposicion(),
                        faltante),
                new FichaProductoResponse.Valorizacion(
                        p.getPrecio(),
                        p.getCostoPromedio(),
                        p.valorizado()),
                new FichaProductoResponse.Resumen(
                        valor(r == null ? null : r.getMovimientos()),
                        valor(r == null ? null : r.getEntradas()),
                        valor(r == null ? null : r.getSalidas()),
                        r == null ? 0 : r.getUnidadesIngresadas(),
                        r == null ? 0 : r.getUnidadesRetiradas(),
                        r == null ? null : r.getUltimoMovimiento()),
                lineas,
                ajusteRepository.findByProductoIdOrderByFechaSolicitudDesc(id).stream()
                        .filter(a -> a.estaPendiente())
                        .toList()
                        .size());
    }

    private static boolean esLaMisma(com.proyecto.microservicio.model.Categoria actual, Long id) {
        return actual != null && actual.getId() != null && actual.getId().equals(id);
    }

    private static long valor(Long numero) {
        return numero == null ? 0L : numero;
    }

    static String normalizarSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}

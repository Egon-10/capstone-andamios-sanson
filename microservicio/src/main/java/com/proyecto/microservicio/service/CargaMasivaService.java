package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.CargaMasivaResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Categoria;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.Proveedor;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.ProveedorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * HU-19: carga masiva de productos desde un archivo CSV, con validación por
 * lotes.
 *
 * El archivo se valida completo antes de guardar nada. Eso permite dos modos:
 *
 * - El modo estricto, que es el de por omisión: si una sola fila falla, no
 *   se guarda ninguna. Es el comportamiento seguro para datos de inventario,
 *   porque una carga a medias deja el catálogo en un estado que nadie pidió y
 *   que hay que deshacer a mano.
 *
 * - El modo parcial, que guarda las filas válidas e informa las demás. Sirve para un
 *   archivo largo del proveedor donde unas pocas filas vienen incompletas.
 *
 * En los dos casos el informe nombra la fila y el motivo de cada rechazo.
 *
 * Las categorías y los proveedores se referencian por nombre, que es lo que
 * trae una planilla, y deben existir: no se crean al pasar. Crear una
 * categoría a partir de un nombre mal escrito ensuciaría el catálogo maestro
 * sin que nadie lo note.
 */
@Service
public class CargaMasivaService {

    private static final String COL_SKU = "sku";
    private static final String COL_NOMBRE = "nombre";
    private static final String COL_PRECIO = "precio";
    private static final String COL_STOCK = "stock";
    private static final String COL_CATEGORIA = "categoria";

    /** Columnas que debe traer la cabecera, en cualquier orden. */
    private static final List<String> COLUMNAS_OBLIGATORIAS =
            List.of(COL_SKU, COL_NOMBRE, COL_PRECIO, COL_STOCK, COL_CATEGORIA);

    private static final int MAXIMO_FILAS = 2000;

    public static final String MODO_TODO_O_NADA = "TODO_O_NADA";
    public static final String MODO_PARCIAL = "PARCIAL";

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final ProveedorRepository proveedores;
    private final AuditoriaService auditoria;

    public CargaMasivaService(ProductoRepository productos,
                              CategoriaRepository categorias,
                              ProveedorRepository proveedores,
                              AuditoriaService auditoria) {
        this.productos = productos;
        this.categorias = categorias;
        this.proveedores = proveedores;
        this.auditoria = auditoria;
    }

    @Transactional
    public CargaMasivaResponse cargar(String contenido, String modo, boolean simulacion,
                                      Long usuarioId) {
        String modoEfectivo = MODO_PARCIAL.equalsIgnoreCase(modo) ? MODO_PARCIAL : MODO_TODO_O_NADA;

        List<List<String>> filas = LectorCsv.leer(contenido);
        if (filas.isEmpty()) {
            throw new ReglaNegocioException("archivo", "El archivo esta vacio");
        }

        Map<String, Integer> columnas = ubicarColumnas(filas.get(0));
        List<List<String>> datos = filas.subList(1, filas.size());

        if (datos.isEmpty()) {
            throw new ReglaNegocioException("archivo",
                    "El archivo solo tiene la cabecera: no hay productos que cargar");
        }
        if (datos.size() > MAXIMO_FILAS) {
            throw new ReglaNegocioException("archivo",
                    "El archivo tiene " + datos.size() + " filas y el maximo es " + MAXIMO_FILAS
                            + ". Dividalo en varios archivos.");
        }

        Map<String, Categoria> porNombreCategoria = indexarCategorias();
        Map<String, Proveedor> porNombreProveedor = indexarProveedores();

        List<CargaMasivaResponse.Fila> errores = new ArrayList<>();
        List<Producto> validos = new ArrayList<>();
        Set<String> skusDelArchivo = new HashSet<>();

        for (int i = 0; i < datos.size(); i++) {
            // La fila 1 es la cabecera, asi que la primera de datos es la 2:
            // el numero debe coincidir con lo que el usuario ve en Excel.
            int numeroDeFila = i + 2;
            List<String> fila = datos.get(i);
            String sku = normalizarSku(valor(fila, columnas, COL_SKU));

            try {
                Producto producto = convertir(fila, columnas, porNombreCategoria,
                        porNombreProveedor, skusDelArchivo);
                validos.add(producto);
                skusDelArchivo.add(producto.getSku());
            } catch (ReglaNegocioException e) {
                errores.add(new CargaMasivaResponse.Fila(numeroDeFila, sku, e.getMessage()));
            }
        }

        boolean hayErrores = !errores.isEmpty();
        boolean seGuarda = !simulacion
                && !validos.isEmpty()
                && (MODO_PARCIAL.equals(modoEfectivo) || !hayErrores);

        List<String> cargados = new ArrayList<>();
        if (seGuarda) {
            productos.saveAll(validos);
            validos.forEach(p -> cargados.add(p.getSku()));

            auditoria.registrar("PRODUCTOS_CARGA_MASIVA",
                    "Carga masiva en modo " + modoEfectivo + ": " + cargados.size()
                            + " productos cargados de " + datos.size() + " filas leidas, "
                            + errores.size() + " rechazadas.",
                    usuarioId);
        }

        return new CargaMasivaResponse(
                modoEfectivo,
                simulacion,
                datos.size(),
                validos.size(),
                errores.size(),
                seGuarda,
                errores,
                cargados);
    }

    /**
     * Convierte una fila en producto, validando cada campo. Lanza una regla de
     * negocio con el motivo cuando la fila no sirve, de modo que el informe
     * pueda explicar el rechazo.
     */
    private Producto convertir(List<String> fila, Map<String, Integer> columnas,
                               Map<String, Categoria> categoriasPorNombre,
                               Map<String, Proveedor> proveedoresPorNombre,
                               Set<String> skusYaVistos) {

        String sku = normalizarSku(valor(fila, columnas, COL_SKU));
        if (sku.isEmpty()) {
            throw new ReglaNegocioException("Falta el SKU");
        }
        if (!sku.matches("^[A-Z0-9-]{3,20}$")) {
            throw new ReglaNegocioException("El SKU " + sku
                    + " debe tener entre 3 y 20 caracteres: letras, numeros o guiones");
        }
        if (skusYaVistos.contains(sku)) {
            throw new ReglaNegocioException("El SKU " + sku + " esta repetido en el archivo");
        }
        if (productos.existsBySkuIgnoreCase(sku)) {
            throw new ReglaNegocioException("El SKU " + sku + " ya existe en el catalogo");
        }

        String nombre = valor(fila, columnas, COL_NOMBRE);
        if (nombre.isEmpty()) {
            throw new ReglaNegocioException("Falta el nombre del producto");
        }
        if (nombre.length() > 100) {
            throw new ReglaNegocioException("El nombre admite hasta 100 caracteres");
        }

        String nombreCategoria = valor(fila, columnas, COL_CATEGORIA);
        Categoria categoria = categoriasPorNombre.get(clave(nombreCategoria));
        if (categoria == null) {
            throw new ReglaNegocioException("La categoria \"" + nombreCategoria
                    + "\" no existe en el catalogo o esta dada de baja. Creela antes de cargar el archivo.");
        }

        Proveedor proveedor = null;
        String nombreProveedor = valor(fila, columnas, "proveedor");
        if (!nombreProveedor.isEmpty()) {
            proveedor = proveedoresPorNombre.get(clave(nombreProveedor));
            if (proveedor == null) {
                throw new ReglaNegocioException("El proveedor \"" + nombreProveedor
                        + "\" no existe en el catalogo");
            }
        }

        Producto p = new Producto();
        p.setSku(sku);
        p.setNombre(nombre);
        p.setDescripcion(vacioComoNulo(valor(fila, columnas, "descripcion")));
        p.setPrecio(comoDecimal(valor(fila, columnas, COL_PRECIO), COL_PRECIO));
        p.setStock(comoEntero(valor(fila, columnas, COL_STOCK), COL_STOCK, true));
        p.setStockMinimo(comoEntero(valor(fila, columnas, "stockminimo"), "stock minimo", false));
        p.setPuntoReposicion(
                comoEntero(valor(fila, columnas, "puntoreposicion"), "punto de reposicion", false));
        p.setStockMaximo(comoEntero(valor(fila, columnas, "stockmaximo"), "stock maximo", false));
        p.setCategoria(categoria);
        p.setProveedor(proveedor);
        p.setActivo(true);

        // El costo promedio arranca en el precio informado, que es el unico dato
        // de costo que trae la planilla. La primera entrada lo corrige con el
        // promedio ponderado real.
        p.setCostoPromedio(p.getPrecio() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(p.getPrecio()));

        if (p.getStockMaximo() != null && p.getStockMinimo() != null
                && p.getStockMaximo() < p.getStockMinimo()) {
            throw new ReglaNegocioException("El stock maximo (" + p.getStockMaximo()
                    + ") no puede ser menor que el minimo (" + p.getStockMinimo() + ")");
        }

        return p;
    }

    /**
     * Ubica cada columna por su nombre, para no depender del orden en que el
     * proveedor haya armado la planilla.
     */
    private static Map<String, Integer> ubicarColumnas(List<String> cabecera) {
        Map<String, Integer> columnas = new HashMap<>();
        for (int i = 0; i < cabecera.size(); i++) {
            columnas.put(clave(cabecera.get(i)), i);
        }
        List<String> faltantes = COLUMNAS_OBLIGATORIAS.stream()
                .filter(c -> !columnas.containsKey(c))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new ReglaNegocioException("archivo",
                    "Faltan columnas obligatorias en la cabecera: " + String.join(", ", faltantes)
                            + ". Se esperaba: sku, nombre, descripcion, precio, stock, "
                            + "stockMinimo, puntoReposicion, stockMaximo, categoria, proveedor.");
        }
        return columnas;
    }

    private Map<String, Categoria> indexarCategorias() {
        Map<String, Categoria> mapa = new HashMap<>();
        // Solo las activas: una categoria dada de baja no recibe productos nuevos.
        categorias.findAll().stream()
                .filter(Categoria::isActivo)
                .forEach(c -> mapa.put(clave(c.getNombre()), c));
        return mapa;
    }

    private Map<String, Proveedor> indexarProveedores() {
        Map<String, Proveedor> mapa = new HashMap<>();
        proveedores.findAll().stream()
                .filter(Proveedor::isActivo)
                .forEach(p -> mapa.put(clave(p.getNombre()), p));
        return mapa;
    }

    private static String valor(List<String> fila, Map<String, Integer> columnas, String columna) {
        Integer indice = columnas.get(columna);
        if (indice == null || indice >= fila.size()) {
            return "";
        }
        return fila.get(indice).trim();
    }

    /** Clave de comparacion: sin espacios, sin tildes y en minusculas. */
    static String clave(String valor) {
        if (valor == null) {
            return "";
        }
        String sinTildes = java.text.Normalizer
                .normalize(valor.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    static String normalizarSku(String sku) {
        return sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
    }

    private static Double comoDecimal(String valor, String campo) {
        if (valor.isEmpty()) {
            throw new ReglaNegocioException("Falta el " + campo);
        }
        try {
            // Una planilla en espanol escribe los decimales con coma.
            double numero = Double.parseDouble(valor.replace(',', '.'));
            if (numero < 0) {
                throw new ReglaNegocioException("El " + campo + " no puede ser negativo");
            }
            return numero;
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("El " + campo + " \"" + valor + "\" no es un numero");
        }
    }

    private static Integer comoEntero(String valor, String campo, boolean obligatorio) {
        if (valor.isEmpty()) {
            if (obligatorio) {
                throw new ReglaNegocioException("Falta el " + campo);
            }
            return null;
        }
        try {
            int numero = Integer.parseInt(valor);
            if (numero < 0) {
                throw new ReglaNegocioException("El " + campo + " no puede ser negativo");
            }
            return numero;
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(
                    "El " + campo + " \"" + valor + "\" no es un numero entero");
        }
    }

    private static String vacioComoNulo(String valor) {
        return valor == null || valor.isEmpty() ? null : valor;
    }
}

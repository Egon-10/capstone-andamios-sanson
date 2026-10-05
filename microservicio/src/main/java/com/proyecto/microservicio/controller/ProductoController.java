package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.CargaMasivaResponse;
import com.proyecto.microservicio.dto.FichaProductoResponse;
import com.proyecto.microservicio.dto.PaginaResponse;
import com.proyecto.microservicio.dto.ProductoRequest;
import com.proyecto.microservicio.dto.ProductoResumen;
import com.proyecto.microservicio.dto.UmbralesRequest;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.security.UsuarioAutenticado;
import com.proyecto.microservicio.service.CargaMasivaService;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.ProductoDTO;
import com.proyecto.microservicio.service.ProductoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    /** Un archivo de carga masiva mas grande que esto no es una planilla de productos. */
    private static final long TAMANO_MAXIMO_ARCHIVO = 2L * 1024 * 1024;

    private final ProductoService service;
    private final CargaMasivaService cargaMasiva;

    public ProductoController(ProductoService service, CargaMasivaService cargaMasiva) {
        this.service = service;
        this.cargaMasiva = cargaMasiva;
    }

    @GetMapping
    public List<Producto> listar() {
        return service.listar();
    }

    @GetMapping("/detalle")
    public List<ProductoDTO> listarDetalle() {
        return service.listarDetalle();
    }

    @GetMapping("/{id}")
    public Producto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<Producto> registrar(@Valid @RequestBody ProductoRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(solicitud));
    }

    @PutMapping("/{id}")
    public Producto actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
    /**
     * HU-21: búsqueda y filtrado con paginación en el servidor.
     *
     * El listado completo de /api/productos se mantiene por compatibilidad con
     * las pantallas que todavía lo usan, pero esta es la ruta que debe usar una
     * lista nueva: devuelve una página y no el catálogo entero.
     */
    @GetMapping("/buscar")
    public PaginaResponse<ProductoResumen> buscar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @RequestParam(defaultValue = "false") boolean porReponer,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "nombre") String orden,
            @RequestParam(defaultValue = "asc") String direccion) {

        return PaginaResponse.de(
                service.buscar(q, categoriaId, proveedorId, soloActivos, porReponer,
                        pagina, tamano, orden, direccion),
                ProductoResumen::de);
    }

    /** HU-22: ficha de detalle consolidada del producto. */
    @GetMapping("/{id}/ficha")
    public FichaProductoResponse ficha(@PathVariable Long id,
                                       @RequestParam(defaultValue = "10") int ultimosMovimientos) {
        return service.ficha(id, ultimosMovimientos);
    }

    /** HU-20: umbrales de reposición, que se editan aparte del resto de la ficha. */
    @PutMapping("/{id}/umbrales")
    public Producto actualizarUmbrales(@PathVariable Long id,
                                       @Valid @RequestBody UmbralesRequest solicitud) {
        return service.actualizarUmbrales(id, solicitud);
    }

    /**
     * HU-19: carga masiva de productos desde un archivo CSV.
     *
     * Con simulacion=true se valida el archivo y se devuelve el informe sin
     * guardar nada, que es la forma razonable de revisar una planilla larga
     * antes de aplicarla. El modo TODO_O_NADA, que es el de por omisión, no
     * guarda ninguna fila si alguna falla.
     */
    @PostMapping("/carga-masiva")
    public CargaMasivaResponse cargaMasiva(@RequestParam("archivo") MultipartFile archivo,
                                           @RequestParam(defaultValue = "TODO_O_NADA") String modo,
                                           @RequestParam(defaultValue = "false") boolean simulacion,
                                           @AuthenticationPrincipal Jwt jwt) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException("archivo", "Adjunte el archivo CSV con los productos");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_ARCHIVO) {
            throw new ReglaNegocioException("archivo",
                    "El archivo supera los 2 MB. Dividalo en varios archivos.");
        }

        String contenido;
        try {
            contenido = new String(archivo.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ReglaNegocioException("archivo", "No se pudo leer el archivo adjunto");
        }

        return cargaMasiva.cargar(contenido, modo, simulacion, UsuarioAutenticado.id(jwt));
    }
}

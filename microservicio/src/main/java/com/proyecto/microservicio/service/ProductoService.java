package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.ProductoRequest;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.ProductoDTO;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.ProveedorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** HU-10: registro de productos con validación en servidor y SKU único. */
@Service
public class ProductoService {

    private final ProductoRepository repository;
    private final CategoriaRepository categoriaRepository;
    private final ProveedorRepository proveedorRepository;

    public ProductoService(ProductoRepository repository, CategoriaRepository categoriaRepository,
                           ProveedorRepository proveedorRepository) {
        this.repository = repository;
        this.categoriaRepository = categoriaRepository;
        this.proveedorRepository = proveedorRepository;
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
        p.setCategoria(categoriaRepository.findById(s.categoria().id())
                .orElseThrow(() -> new ReglaNegocioException("categoria", "La categoría seleccionada no existe")));
        if (s.proveedor() != null && s.proveedor().id() != null) {
            p.setProveedor(proveedorRepository.findById(s.proveedor().id())
                    .orElseThrow(() -> new ReglaNegocioException("proveedor", "El proveedor seleccionado no existe")));
        } else {
            p.setProveedor(null);
        }
    }

    static String normalizarSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}

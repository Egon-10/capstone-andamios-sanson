package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.ProveedorRequest;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.model.Proveedor;
import com.proyecto.microservicio.repository.ProveedorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * HU-11: catalogo maestro de proveedores. Igual que las categorias, la baja
 * es logica para no dejar productos apuntando a un proveedor borrado.
 */
@Service
public class ProveedorService {

    private final ProveedorRepository repository;

    public ProveedorService(ProveedorRepository repository) {
        this.repository = repository;
    }

    public List<Proveedor> listar(boolean incluirInactivos) {
        return incluirInactivos
                ? repository.findAllByOrderByNombreAsc()
                : repository.findByActivoTrueOrderByNombreAsc();
    }

    public Proveedor obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado"));
    }

    @Transactional
    public Proveedor registrar(ProveedorRequest s) {
        String ruc = CategoriaService.limpiar(s.ruc());
        if (ruc != null && repository.existsByRuc(ruc)) {
            throw new ConflictoException("ruc", "Ya existe un proveedor con el RUC " + ruc);
        }
        Proveedor p = new Proveedor();
        aplicar(p, s, ruc);
        p.setActivo(true);
        p.setFechaCreacion(ZonaHoraria.ahora());
        return repository.save(p);
    }

    @Transactional
    public Proveedor actualizar(Long id, ProveedorRequest s) {
        Proveedor p = obtener(id);
        String ruc = CategoriaService.limpiar(s.ruc());
        if (ruc != null && repository.existsByRucAndIdNot(ruc, id)) {
            throw new ConflictoException("ruc", "Ya existe un proveedor con el RUC " + ruc);
        }
        aplicar(p, s, ruc);
        return repository.save(p);
    }

    @Transactional
    public Proveedor darDeBaja(Long id) {
        Proveedor p = obtener(id);
        p.setActivo(false);
        return repository.save(p);
    }

    @Transactional
    public Proveedor reactivar(Long id) {
        Proveedor p = obtener(id);
        p.setActivo(true);
        return repository.save(p);
    }

    private static void aplicar(Proveedor p, ProveedorRequest s, String ruc) {
        p.setNombre(s.nombre().trim());
        p.setRuc(ruc);
        p.setDireccion(CategoriaService.limpiar(s.direccion()));
        p.setTelefono(CategoriaService.limpiar(s.telefono()));
        p.setCorreo(CategoriaService.limpiar(s.correo()));
    }
}

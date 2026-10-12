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
    private final AuditoriaService auditoria;

    public ProveedorService(ProveedorRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
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
        return auditar(repository.save(p), "PROVEEDOR_CREADO");
    }

    @Transactional
    public Proveedor actualizar(Long id, ProveedorRequest s) {
        Proveedor p = obtener(id);
        String ruc = CategoriaService.limpiar(s.ruc());
        if (ruc != null && repository.existsByRucAndIdNot(ruc, id)) {
            throw new ConflictoException("ruc", "Ya existe un proveedor con el RUC " + ruc);
        }
        aplicar(p, s, ruc);
        return auditar(repository.save(p), "PROVEEDOR_EDITADO");
    }

    @Transactional
    public Proveedor darDeBaja(Long id) {
        Proveedor p = obtener(id);
        p.setActivo(false);
        return auditar(repository.save(p), "PROVEEDOR_BAJA");
    }

    @Transactional
    public Proveedor reactivar(Long id) {
        Proveedor p = obtener(id);
        p.setActivo(true);
        return auditar(repository.save(p), "PROVEEDOR_REACTIVADO");
    }

    private static void aplicar(Proveedor p, ProveedorRequest s, String ruc) {
        p.setNombre(s.nombre().trim());
        p.setRuc(ruc);
        p.setDireccion(CategoriaService.limpiar(s.direccion()));
        p.setTelefono(CategoriaService.limpiar(s.telefono()));
        p.setCorreo(CategoriaService.limpiar(s.correo()));
    }

    private Proveedor auditar(Proveedor guardado, String accion) {
        auditoria.registrarComoUsuarioActual(accion, "Proveedor " + guardado.getId() + ": " + guardado.getNombre());
        return guardado;
    }
}

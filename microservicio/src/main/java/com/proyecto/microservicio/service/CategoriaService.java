package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.CategoriaRequest;
import com.proyecto.microservicio.exception.ConflictoException;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.model.Categoria;
import com.proyecto.microservicio.repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * HU-11: catalogo maestro de categorias.
 *
 * La baja es logica: dar de baja una categoria la saca de las listas para
 * registrar productos, pero no la borra. Borrarla fallaria por la clave ajena
 * de los productos que la usan, o peor, dejaria esos productos sin
 * clasificacion historica.
 */
@Service
public class CategoriaService {

    private final CategoriaRepository repository;
    private final AuditoriaService auditoria;

    public CategoriaService(CategoriaRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    /** Por omision solo las activas, que son las que se pueden asignar. */
    public List<Categoria> listar(boolean incluirInactivas) {
        return incluirInactivas
                ? repository.findAllByOrderByNombreAsc()
                : repository.findByActivoTrueOrderByNombreAsc();
    }

    public Categoria obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada"));
    }

    @Transactional
    public Categoria registrar(CategoriaRequest s) {
        String nombre = s.nombre().trim();
        if (repository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("nombre", "Ya existe la categoría " + nombre);
        }
        Categoria c = new Categoria();
        c.setNombre(nombre);
        c.setDescripcion(limpiar(s.descripcion()));
        c.setActivo(true);
        c.setFechaCreacion(ZonaHoraria.ahora());
        return auditar(repository.save(c), "CATEGORIA_CREADA");
    }

    @Transactional
    public Categoria actualizar(Long id, CategoriaRequest s) {
        Categoria c = obtener(id);
        String nombre = s.nombre().trim();
        if (repository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new ConflictoException("nombre", "Ya existe la categoría " + nombre);
        }
        c.setNombre(nombre);
        c.setDescripcion(limpiar(s.descripcion()));
        return auditar(repository.save(c), "CATEGORIA_EDITADA");
    }

    /** Baja logica: la categoria deja de ofrecerse pero no se borra. */
    @Transactional
    public Categoria darDeBaja(Long id) {
        Categoria c = obtener(id);
        c.setActivo(false);
        return auditar(repository.save(c), "CATEGORIA_BAJA");
    }

    @Transactional
    public Categoria reactivar(Long id) {
        Categoria c = obtener(id);
        c.setActivo(true);
        return auditar(repository.save(c), "CATEGORIA_REACTIVADA");
    }

    static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private Categoria auditar(Categoria guardado, String accion) {
        auditoria.registrarComoUsuarioActual(accion, "Categoría " + guardado.getId() + ": " + guardado.getNombre());
        return guardado;
    }
}

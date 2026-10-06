package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.CategoriaRequest;
import com.proyecto.microservicio.model.Categoria;
import com.proyecto.microservicio.service.CategoriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HU-11: catalogo maestro de categorias.
 *
 * DELETE ya no borra la fila: hace una baja logica. Borrarla fallaba por la
 * clave ajena de los productos que la usan, y si no fallaba, dejaba esos
 * productos sin su dato historico.
 */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Categoria> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return service.listar(incluirInactivos);
    }

    @GetMapping("/{id}")
    public Categoria obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<Categoria> registrar(@Valid @RequestBody CategoriaRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(solicitud));
    }

    @PutMapping("/{id}")
    public Categoria actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Long id) {
        service.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivacion")
    public Categoria reactivar(@PathVariable Long id) {
        return service.reactivar(id);
    }
}

package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.ProveedorRequest;
import com.proyecto.microservicio.model.Proveedor;
import com.proyecto.microservicio.service.ProveedorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HU-11: catalogo maestro de proveedores.
 *
 * DELETE ya no borra la fila: hace una baja logica. Borrarla fallaba por la
 * clave ajena de los productos que la usan, y si no fallaba, dejaba esos
 * productos sin su dato historico.
 */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService service;

    public ProveedorController(ProveedorService service) {
        this.service = service;
    }

    @GetMapping
    public List<Proveedor> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return service.listar(incluirInactivos);
    }

    @GetMapping("/{id}")
    public Proveedor obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<Proveedor> registrar(@Valid @RequestBody ProveedorRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(solicitud));
    }

    @PutMapping("/{id}")
    public Proveedor actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Long id) {
        service.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivacion")
    public Proveedor reactivar(@PathVariable Long id) {
        return service.reactivar(id);
    }
}

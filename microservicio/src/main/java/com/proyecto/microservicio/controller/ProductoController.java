package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.ProductoRequest;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.ProductoDTO;
import com.proyecto.microservicio.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
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
}

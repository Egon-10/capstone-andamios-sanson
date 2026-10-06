package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.service.RolService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolService service;

    public RolController(RolService service) {
        this.service = service;
    }

    @GetMapping
    public List<Rol> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Rol obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public Rol guardar(@RequestBody Rol rol) {
        return service.guardar(rol);
    }

    @PutMapping("/{id}")
    public Rol actualizar(
            @PathVariable Long id,
            @RequestBody Rol rol) {

        rol.setId(id);
        return service.guardar(rol);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}
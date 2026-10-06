package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.model.Movimiento;
import com.proyecto.microservicio.model.MovimientoDTO;
import com.proyecto.microservicio.service.MovimientoService;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private final MovimientoService service;

    public MovimientoController(MovimientoService service) {
        this.service = service;
    }

    @GetMapping
    public List<MovimientoDTO> listar() {
        return service.listarMovimientos();
    }

    @PostMapping("/entrada")
    public Movimiento entrada(
            @RequestParam Long productoId,
            @RequestParam Integer cantidad,
            @RequestParam Long usuarioId) {

        return service.registrarEntrada(
                productoId,
                cantidad,
                usuarioId);
    }

    @PostMapping("/salida")
    public Movimiento salida(
            @RequestParam Long productoId,
            @RequestParam Integer cantidad,
            @RequestParam Long usuarioId) {

        return service.registrarSalida(
                productoId,
                cantidad,
                usuarioId);
    }
@DeleteMapping("/{id}")
public void eliminar(
        @PathVariable Long id){

    service.eliminarMovimiento(id);
}
    
@PutMapping("/{id}")
public Movimiento actualizar(
        @PathVariable Long id,
        @RequestBody Movimiento movimiento) {

    return service.actualizarMovimiento(
            id,
            movimiento.getCantidad()
    );
}
@GetMapping("/filtrar")
public List<MovimientoDTO> filtrar(
        @RequestParam(required = false) String fechaInicio,
        @RequestParam(required = false) String fechaFin,
        @RequestParam(required = false) String tipo) {

    LocalDateTime inicio = null;
    LocalDateTime fin = null;

    if (fechaInicio != null && !fechaInicio.isEmpty()) {
        inicio = LocalDateTime.parse(
                fechaInicio + "T00:00:00"
        );
    }

    if (fechaFin != null && !fechaFin.isEmpty()) {
        fin = LocalDateTime.parse(
                fechaFin + "T23:59:59"
        );
    }

    if (tipo != null && tipo.isEmpty()) {
        tipo = null;
    }

    return service.filtrarMovimientos(
            inicio,
            fin,
            tipo
    );
}
}
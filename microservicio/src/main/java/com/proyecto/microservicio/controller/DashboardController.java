package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.model.DashboardDTO;
import com.proyecto.microservicio.model.MovimientoDTO;
import com.proyecto.microservicio.service.DashboardService;


import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(
            DashboardService service) {

        this.service = service;
    }

    @GetMapping("/resumen")
    public DashboardDTO resumen() {

        return service.obtenerResumen();
    }

    @GetMapping("/ultimos-movimientos")
    public List<MovimientoDTO> ultimosMovimientos() {

        return service.obtenerUltimosMovimientos();
    }

   @GetMapping("/productos-por-categoria")
public List<Object[]> productosPorCategoria() {

    return service.productosPorCategoria();
}

@GetMapping("/resumen-movimientos")
public List<Object[]> resumenMovimientos() {

    return service.resumenMovimientos();
}

@GetMapping("/productos-mayor-stock")
public List<Object[]> productosMayorStock() {

    return service.productosMayorStock();
}

@GetMapping("/stock-critico")
public List<Object[]> stockCritico() {

    return service.obtenerProductosStockCritico();
}
@GetMapping("/movimientos-por-dia")
public List<Object[]> movimientosPorDia() {

    return service.obtenerMovimientosPorDia();
}
@GetMapping("/movimientos-semana/{offset}")
public List<Object[]> movimientosSemana(
        @PathVariable Integer offset) {

    return service
            .obtenerMovimientosPorSemana(offset);
}

}
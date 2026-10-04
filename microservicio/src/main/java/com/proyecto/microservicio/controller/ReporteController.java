package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.service.ReporteService;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService service;

    public ReporteController(
            ReporteService service) {

        this.service = service;
    }

    @GetMapping("/movimientos")
public ResponseEntity<InputStreamResource>
descargarReporte(

        @RequestParam(
                defaultValue = "TODOS"
        )
        String tipo,

        @RequestParam(required = false)
        String fechaInicio,

        @RequestParam(required = false)
        String fechaFin
){

    ByteArrayInputStream pdf =
            service.generarReporteMovimientos(
                    tipo,
                    fechaInicio,
                    fechaFin
            );

    HttpHeaders headers =
            new HttpHeaders();

    headers.add(
            "Content-Disposition",
            "inline; filename=reporte_movimientos.pdf"
    );

    return ResponseEntity.ok()
            .headers(headers)
            .contentType(
                    MediaType.APPLICATION_PDF
            )
            .body(
                    new InputStreamResource(
                            pdf
                    )
            );
}
@GetMapping("/productos")
public ResponseEntity<InputStreamResource>
descargarProductos(

        @RequestParam(
                required = false
        )
        String categoria
){

    ByteArrayInputStream pdf =
            service.generarReporteProductos(
                    categoria
            );

    HttpHeaders headers =
            new HttpHeaders();

    headers.add(
            "Content-Disposition",
            "inline; filename=reporte_productos.pdf"
    );

    return ResponseEntity.ok()
            .headers(headers)
            .contentType(
                    MediaType.APPLICATION_PDF
            )
            .body(
                    new InputStreamResource(pdf)
            );
}





@GetMapping("/stock-critico")
public ResponseEntity<InputStreamResource>
reporteStockCritico(

        @RequestParam(
                required = false
        )
        String categoria
){

    ByteArrayInputStream pdf =
            service.generarReporteStockCritico(
                    categoria
            );

    return ResponseEntity.ok()
            .contentType(
                    MediaType.APPLICATION_PDF
            )
            .body(
                    new InputStreamResource(pdf)
            );
}
@GetMapping("/auditoria")
public ResponseEntity<InputStreamResource>
reporteAuditoria(

        @RequestParam(required = false)
        String accion,

        @RequestParam(required = false)
        String fechaInicio,

        @RequestParam(required = false)
        String fechaFin
){

    ByteArrayInputStream pdf =
            service.generarReporteAuditoria(
                    accion,
                    fechaInicio,
                    fechaFin
            );

    return ResponseEntity.ok()
            .contentType(
                    MediaType.APPLICATION_PDF
            )
            .body(
                    new InputStreamResource(pdf)
            );
}

@GetMapping("/usuarios")
public ResponseEntity<InputStreamResource>
reporteUsuarios(

        @RequestParam(
                required = false
        )
        String rol
){

    ByteArrayInputStream pdf =
            service.generarReporteUsuarios(
                    rol
            );

    return ResponseEntity.ok()
            .contentType(
                    MediaType.APPLICATION_PDF
            )
            .body(
                    new InputStreamResource(pdf)
            );
}
}
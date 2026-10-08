package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.AccesoResponse;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.dto.PaginaResponse;
import com.proyecto.microservicio.service.AccesoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** HU-32: consulta de la bitácora de accesos. Solo el administrador (SecurityConfig). */
@RestController
@RequestMapping("/api/accesos")
public class AccesoController {

    private final AccesoService service;

    public AccesoController(AccesoService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<AccesoResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        FiltroBitacora filtro = new FiltroBitacora(texto, usuarioId, resultado, desde, hasta);
        return PaginaResponse.de(service.buscar(filtro, pagina, tamano), AccesoResponse::de);
    }
}

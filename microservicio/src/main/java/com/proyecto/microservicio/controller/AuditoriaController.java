package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.AuditoriaResponse;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.dto.PaginaResponse;
import com.proyecto.microservicio.service.AuditoriaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * HU-33: consulta de la bitácora de auditoría.
 *
 * Solo lectura. Ya no existe un POST para que el cliente registre acciones:
 * la bitácora la escribe el servidor al ejecutar cada operación, de modo que
 * no se puede omitir ni fabricar desde el navegador.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<AuditoriaResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        FiltroBitacora filtro = new FiltroBitacora(texto, usuarioId, null, desde, hasta);
        return PaginaResponse.de(service.buscar(filtro, pagina, tamano), AuditoriaResponse::de);
    }

    @GetMapping("/ultimos")
    public List<AuditoriaResponse> ultimos() {
        return service.listarUltimos().stream().map(AuditoriaResponse::de).toList();
    }
}

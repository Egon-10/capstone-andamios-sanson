package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.dto.AnulacionRequest;
import com.proyecto.microservicio.dto.MovimientoRequest;
import com.proyecto.microservicio.dto.MovimientoResponse;
import com.proyecto.microservicio.model.MovimientoDTO;
import com.proyecto.microservicio.security.UsuarioAutenticado;
import com.proyecto.microservicio.service.MovimientoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Movimientos de inventario.
 *
 * El identificador de quien registra el movimiento ya no se recibe como
 * parámetro: se toma del token de la sesión (CR-04). La versión anterior
 * aceptaba un usuarioId enviado por el cliente, de modo que cualquier usuario
 * con una sesión válida podía atribuir un movimiento a otra persona y dejar la
 * auditoría apuntando a quien no lo hizo.
 *
 * Tampoco hay PUT ni DELETE: un movimiento es inmutable. Para corregirlo se
 * usa POST /api/movimientos/{id}/anulacion, que genera el asiento
 * compensatorio de la HU-15 y conserva la traza en el kardex.
 */
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

    @GetMapping("/{id}")
    public MovimientoResponse obtener(@PathVariable Long id) {
        return MovimientoResponse.de(service.obtener(id));
    }

    /** HU-12: ingreso de mercadería al almacén. */
    @PostMapping("/entrada")
    public ResponseEntity<MovimientoResponse> entrada(@Valid @RequestBody MovimientoRequest solicitud,
                                                      @AuthenticationPrincipal Jwt jwt) {
        MovimientoResponse creado = MovimientoResponse.de(
                service.registrarEntrada(solicitud, UsuarioAutenticado.id(jwt)));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /** HU-13: salida de mercadería, validando la disponibilidad. */
    @PostMapping("/salida")
    public ResponseEntity<MovimientoResponse> salida(@Valid @RequestBody MovimientoRequest solicitud,
                                                     @AuthenticationPrincipal Jwt jwt) {
        MovimientoResponse creado = MovimientoResponse.de(
                service.registrarSalida(solicitud, UsuarioAutenticado.id(jwt)));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * HU-15: anula un movimiento. Devuelve el asiento compensatorio que se
     * generó, no el original, porque es el nuevo asiento el que corrige el
     * inventario y el que el cliente debe mostrar en el kardex.
     */
    @PostMapping("/{id}/anulacion")
    public ResponseEntity<MovimientoResponse> anular(@PathVariable Long id,
                                                     @Valid @RequestBody AnulacionRequest solicitud,
                                                     @AuthenticationPrincipal Jwt jwt) {
        MovimientoResponse compensatorio = MovimientoResponse.de(
                service.anular(id, solicitud, UsuarioAutenticado.id(jwt)));
        return ResponseEntity.status(HttpStatus.CREATED).body(compensatorio);
    }

    @GetMapping("/filtrar")
    public List<MovimientoDTO> filtrar(@RequestParam(required = false) String fechaInicio,
                                       @RequestParam(required = false) String fechaFin,
                                       @RequestParam(required = false) String tipo) {
        return service.filtrarMovimientos(
                inicioDelDia(fechaInicio),
                finDelDia(fechaFin),
                vacioComoNulo(tipo));
    }

    /**
     * Convierte una fecha en el primer instante del día. Antes se concatenaba
     * el texto "T00:00:00" y se parseaba, de modo que una fecha mal escrita
     * provocaba un error 500; ahora se valida como fecha y el formato
     * incorrecto se reporta como solicitud inválida.
     */
    private static LocalDateTime inicioDelDia(String fecha) {
        LocalDate dia = comoFecha(fecha);
        return dia == null ? null : dia.atStartOfDay();
    }

    private static LocalDateTime finDelDia(String fecha) {
        LocalDate dia = comoFecha(fecha);
        return dia == null ? null : dia.atTime(LocalTime.MAX);
    }

    private static LocalDate comoFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(fecha.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new com.proyecto.microservicio.exception.ReglaNegocioException(
                    "fecha", "La fecha " + fecha + " no tiene el formato AAAA-MM-DD");
        }
    }

    private static String vacioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

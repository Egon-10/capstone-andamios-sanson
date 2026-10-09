package com.proyecto.microservicio.controller;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.FiltroBitacora;
import com.proyecto.microservicio.reporte.Formato;
import com.proyecto.microservicio.reporte.RenderizadorExcel;
import com.proyecto.microservicio.reporte.RenderizadorPdf;
import com.proyecto.microservicio.reporte.Reporte;
import com.proyecto.microservicio.security.UsuarioAutenticado;
import com.proyecto.microservicio.service.AuditoriaService;
import com.proyecto.microservicio.service.ReporteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * HU-26 y HU-27: exportación de reportes en PDF o Excel.
 *
 * Cada ruta corresponde a un reporte y recibe los mismos filtros que la
 * pantalla de la que sale. El formato se elige con el parámetro
 * {@code formato} (pdf por omisión, o xlsx). Quién puede pedir cada reporte lo
 * define SecurityConfig, con el mismo criterio que la consulta en pantalla.
 *
 * Cada exportación queda en la bitácora de auditoría: un reporte saca
 * información del sistema, y saber quién la sacó y cuándo es parte del control.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final String ISO = "yyyy-MM-dd";

    private final ReporteService reportes;
    private final AuditoriaService auditoria;
    private final RenderizadorPdf pdf = new RenderizadorPdf();
    private final RenderizadorExcel excel = new RenderizadorExcel();

    public ReporteController(ReporteService reportes, AuditoriaService auditoria) {
        this.reportes = reportes;
        this.auditoria = auditoria;
    }

    @GetMapping("/movimientos")
    public ResponseEntity<byte[]> movimientos(@RequestParam(required = false) String tipo,
                                              @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate desde,
                                              @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate hasta,
                                              @RequestParam(required = false) String formato,
                                              @AuthenticationPrincipal Jwt jwt) {
        Long usuario = UsuarioAutenticado.id(jwt);
        return entregar(reportes.movimientos(tipo, desde, hasta, usuario), formato, "movimientos", usuario);
    }

    @GetMapping("/productos")
    public ResponseEntity<byte[]> productos(@RequestParam(required = false) Long categoriaId,
                                            @RequestParam(required = false) String formato,
                                            @AuthenticationPrincipal Jwt jwt) {
        Long usuario = UsuarioAutenticado.id(jwt);
        return entregar(reportes.productos(categoriaId, usuario), formato, "productos", usuario);
    }

    @GetMapping("/stock-critico")
    public ResponseEntity<byte[]> stockCritico(@RequestParam(required = false) String formato,
                                               @AuthenticationPrincipal Jwt jwt) {
        Long usuario = UsuarioAutenticado.id(jwt);
        return entregar(reportes.stockCritico(usuario), formato, "stock-critico", usuario);
    }

    /** HU-28: sin fecha, la valorización vigente; con fecha, al cierre de ese día. */
    @GetMapping("/valorizacion")
    public ResponseEntity<byte[]> valorizacion(@RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate fecha,
                                               @RequestParam(required = false) String formato,
                                               @AuthenticationPrincipal Jwt jwt) {
        Long usuario = UsuarioAutenticado.id(jwt);
        return entregar(reportes.valorizacion(fecha, usuario), formato, "valorizacion", usuario);
    }

    @GetMapping("/kardex/{productoId}")
    public ResponseEntity<byte[]> kardex(@PathVariable Long productoId,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate hasta,
                                         @RequestParam(required = false) String formato,
                                         @AuthenticationPrincipal Jwt jwt) {
        Long usuario = UsuarioAutenticado.id(jwt);
        return entregar(reportes.kardex(productoId, desde, hasta, usuario), formato, "kardex-" + productoId, usuario);
    }

    @GetMapping("/auditoria")
    public ResponseEntity<byte[]> auditoria(@RequestParam(required = false) String texto,
                                            @RequestParam(required = false) Long usuarioId,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate desde,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate hasta,
                                            @RequestParam(required = false) String formato,
                                            @AuthenticationPrincipal Jwt jwt) {
        Long usuario = UsuarioAutenticado.id(jwt);
        FiltroBitacora filtro = new FiltroBitacora(texto, usuarioId, null, desde, hasta);
        return entregar(reportes.auditoria(filtro, usuario), formato, "auditoria", usuario);
    }

    @GetMapping("/accesos")
    public ResponseEntity<byte[]> accesos(@RequestParam(required = false) String texto,
                                          @RequestParam(required = false) Long usuarioId,
                                          @RequestParam(required = false) String resultado,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDate hasta,
                                          @RequestParam(required = false) String formato,
                                          @AuthenticationPrincipal Jwt jwt) {
        Long usuario = UsuarioAutenticado.id(jwt);
        FiltroBitacora filtro = new FiltroBitacora(texto, usuarioId, resultado, desde, hasta);
        return entregar(reportes.accesos(filtro, usuario), formato, "accesos", usuario);
    }

    @GetMapping("/usuarios")
    public ResponseEntity<byte[]> usuarios(@RequestParam(required = false) String texto,
                                           @RequestParam(required = false) Long rolId,
                                           @RequestParam(required = false) String estado,
                                           @RequestParam(required = false) String formato,
                                           @AuthenticationPrincipal Jwt jwt) {
        Long usuario = UsuarioAutenticado.id(jwt);
        return entregar(reportes.usuarios(texto, rolId, estado, usuario), formato, "usuarios", usuario);
    }

    private ResponseEntity<byte[]> entregar(Reporte reporte, String formatoPedido, String nombre, Long usuario) {
        Formato formato = Formato.desde(formatoPedido);
        byte[] contenido = formato == Formato.EXCEL ? excel.dibujar(reporte) : pdf.dibujar(reporte);
        String archivo = "reporte-" + nombre + "-" + ZonaHoraria.ahora().format(SELLO) + "." + formato.extension();

        auditoria.registrar("REPORTE_EXPORTADO",
                reporte.titulo() + " en " + formato.extension().toUpperCase() + " (" + reporte.filas().size()
                        + " filas). " + String.join(". ", reporte.filtros()),
                usuario);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(formato.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo, StandardCharsets.UTF_8).build().toString())
                // Un reporte puede tener datos económicos o personales: que no
                // quede guardado en cachés intermedias ni del navegador.
                .cacheControl(CacheControl.noStore())
                .body(contenido);
    }
}

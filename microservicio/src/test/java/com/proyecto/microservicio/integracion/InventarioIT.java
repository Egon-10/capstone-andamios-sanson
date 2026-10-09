package com.proyecto.microservicio.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración del inventario: consultas nativas contra MySQL,
 * transacciones de movimientos, valorización a fecha, bitácoras y reportes.
 */
class InventarioIT extends IntegracionBase {

    private Long productoConStock(String token) {
        return jdbc().queryForObject("SELECT MIN(id) FROM productos WHERE activo = 1 AND stock > 10", Long.class);
    }

    @Test
    @DisplayName("PI-07: una entrada actualiza stock, costo promedio y deja el costo resultante en el movimiento (HU-12, HU-28)")
    void entradaGuardaCostoResultante() throws Exception {
        String admin = token(crearCuenta(ROL_ADMINISTRADOR));
        Long producto = productoConStock(admin);
        Integer stockAntes = jdbc().queryForObject("SELECT stock FROM productos WHERE id = ?", Integer.class, producto);

        HttpResponse<String> r = post("/api/movimientos/entrada", admin,
                "{\"productoId\":" + producto + ",\"cantidad\":10,\"motivo\":\"COMPRA\",\"costoUnitario\":123.45}");
        assertEquals(201, r.statusCode(), r.body());

        Integer stockDespues = jdbc().queryForObject("SELECT stock FROM productos WHERE id = ?", Integer.class, producto);
        assertEquals(stockAntes + 10, stockDespues);
        BigDecimal resultante = jdbc().queryForObject(
                "SELECT costo_promedio_resultante FROM movimientos WHERE producto_id = ? ORDER BY id DESC LIMIT 1",
                BigDecimal.class, producto);
        BigDecimal vigente = jdbc().queryForObject("SELECT costo_promedio FROM productos WHERE id = ?",
                BigDecimal.class, producto);
        assertEquals(0, vigente.compareTo(resultante));
    }

    @Test
    @DisplayName("PI-08: la valorización con corte ejecuta la consulta nativa y devuelve la fecha (HU-28)")
    void valorizacionAlCorte() throws Exception {
        String gerente = token(crearCuenta(ROL_GERENTE));
        LocalDate ayer = LocalDate.now(ZoneId.of("America/Lima")).minusDays(1);

        HttpResponse<String> r = get("/api/inventario/valorizacion?fecha=" + ayer, gerente);

        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"fechaCorte\":\"" + ayer + "\""));
        assertEquals(422, get("/api/inventario/valorizacion?fecha=" + ayer.plusDays(5), gerente).statusCode());
    }

    @Test
    @DisplayName("PI-09: la consulta de auditoría acepta comodines como texto literal y filtra por usuario (HU-33)")
    void auditoriaConFiltros() throws Exception {
        Cuenta admin = crearCuenta(ROL_ADMINISTRADOR);
        String t = token(admin);
        String texto = URLEncoder.encode("50%_x", StandardCharsets.UTF_8);

        assertEquals(200, get("/api/auditoria?texto=" + texto, t).statusCode());
        HttpResponse<String> propia = get("/api/auditoria?usuarioId=" + admin.id() + "&texto=inicio", t);
        assertEquals(200, propia.statusCode());
        assertTrue(propia.body().contains("\"totalElementos\":1"), propia.body());
        assertFalse(propia.body().contains("numeroDocumento"), "la bitácora no expone datos personales");
    }

    @Test
    @DisplayName("PI-10: la búsqueda paginada de usuarios filtra y ordena en el servidor (HU-30)")
    void busquedaDeUsuarios() throws Exception {
        String admin = token(crearCuenta(ROL_ADMINISTRADOR));
        Cuenta buscada = crearCuenta(ROL_ENCARGADO);

        HttpResponse<String> r = get("/api/usuarios?texto=" + buscada.usuario() + "&estado=ACTIVO&tamano=5", admin);

        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"totalElementos\":1"), r.body());
        assertTrue(r.body().contains(buscada.usuario()));
    }

    @Test
    @DisplayName("PI-11: los reportes se generan en PDF y en Excel con datos reales (HU-26, HU-27)")
    void reportes() throws Exception {
        String admin = token(crearCuenta(ROL_ADMINISTRADOR));

        HttpResponse<byte[]> pdf = getBytes("/api/reportes/movimientos", admin);
        assertEquals(200, pdf.statusCode());
        assertEquals("%PDF", new String(pdf.body(), 0, 4, StandardCharsets.US_ASCII));

        HttpResponse<byte[]> xlsx = getBytes("/api/reportes/valorizacion?formato=xlsx", admin);
        assertEquals(200, xlsx.statusCode());
        assertEquals('P', (char) xlsx.body()[0]);
        assertTrue(xlsx.headers().firstValue("Content-Disposition").orElse("").contains(".xlsx"));

        for (String ruta : new String[]{"/api/reportes/productos", "/api/reportes/stock-critico",
                "/api/reportes/auditoria?formato=xlsx", "/api/reportes/accesos", "/api/reportes/usuarios?formato=xlsx"}) {
            assertEquals(200, getBytes(ruta, admin).statusCode(), ruta);
        }
        Integer exportados = jdbc().queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'REPORTE_EXPORTADO'", Integer.class);
        assertTrue(exportados >= 7);
    }

    @Test
    @DisplayName("PI-12: los indicadores y el stock crítico responden con la base migrada (HU-23 a HU-25)")
    void indicadores() throws Exception {
        String gerente = token(crearCuenta(ROL_GERENTE));
        assertEquals(200, get("/api/indicadores/resumen", gerente).statusCode());
        assertEquals(200, get("/api/indicadores/tendencia", gerente).statusCode());
        assertEquals(200, get("/api/indicadores/stock-critico", gerente).statusCode());
    }
}

package com.proyecto.microservicio.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración de seguridad: autenticación, autorización por rol,
 * bloqueo, sesiones y cabeceras, contra la aplicación y la base reales.
 */
class SeguridadIT extends IntegracionBase {

    @Test
    @DisplayName("PI-01: inicio de sesión correcto y registro en la bitácora de accesos (HU-03, HU-32)")
    void inicioDeSesion() throws Exception {
        Cuenta c = crearCuenta(ROL_ENCARGADO);

        HttpResponse<String> r = login(c.usuario(), c.password());

        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"debeCambiarPassword\":false"));
        Integer exitosos = jdbc().queryForObject(
                "SELECT COUNT(*) FROM accesos WHERE usuario_id = ? AND resultado = 'EXITOSO'", Integer.class, c.id());
        assertEquals(1, exitosos);
    }

    @Test
    @DisplayName("PI-02: cinco fallos bloquean la cuenta; el bloqueo se guarda aunque el login falle (HU-35)")
    void bloqueoPorIntentos() throws Exception {
        Cuenta c = crearCuenta(ROL_ENCARGADO);

        for (int i = 1; i <= 4; i++) {
            assertEquals(401, login(c.usuario(), "Incorrecta#2026").statusCode(), "intento " + i);
        }
        HttpResponse<String> quinto = login(c.usuario(), "Incorrecta#2026");
        assertEquals(423, quinto.statusCode());
        assertNotNull(quinto.headers().firstValue("Retry-After").orElse(null));

        // Con la cuenta bloqueada, ni la contraseña correcta entra.
        assertEquals(423, login(c.usuario(), c.password()).statusCode());

        Integer bloqueados = jdbc().queryForObject(
                "SELECT COUNT(*) FROM accesos WHERE usuario_id = ? AND resultado = 'BLOQUEADO'", Integer.class, c.id());
        assertEquals(2, bloqueados);
        assertNotNull(jdbc().queryForObject("SELECT bloqueado_hasta FROM usuarios WHERE id = ?", Object.class, c.id()));
    }

    @Test
    @DisplayName("PI-03: matriz de permisos por rol aplicada en el servidor (HU-05, HU-26, HU-32)")
    void matrizDePermisos() throws Exception {
        String encargado = token(crearCuenta(ROL_ENCARGADO));
        String gerente = token(crearCuenta(ROL_GERENTE));
        String admin = token(crearCuenta(ROL_ADMINISTRADOR));

        assertEquals(200, get("/api/productos", encargado).statusCode());
        assertEquals(403, get("/api/reportes/valorizacion", encargado).statusCode());
        assertEquals(403, get("/api/inventario/valorizacion", encargado).statusCode());
        assertEquals(403, get("/api/accesos", encargado).statusCode());
        assertEquals(403, get("/api/usuarios", gerente).statusCode());
        assertEquals(403, get("/api/accesos", gerente).statusCode());
        assertEquals(200, get("/api/auditoria", gerente).statusCode());
        assertEquals(200, get("/api/usuarios/opciones", gerente).statusCode());
        assertEquals(200, get("/api/accesos", admin).statusCode());
        assertEquals(401, get("/api/productos", null).statusCode());
    }

    @Test
    @DisplayName("PI-04: desactivar una cuenta invalida su sesión abierta en la siguiente solicitud (HU-31)")
    void desactivacionInmediata() throws Exception {
        String admin = token(crearCuenta(ROL_ADMINISTRADOR));
        Cuenta luis = crearCuenta(ROL_ENCARGADO);
        String sesionDeLuis = token(luis);
        assertEquals(200, get("/api/productos", sesionDeLuis).statusCode());

        HttpResponse<String> baja = patch("/api/usuarios/" + luis.id() + "/estado", admin,
                "{\"estado\":\"INACTIVO\",\"motivo\":\"Prueba de integracion\"}");
        assertEquals(200, baja.statusCode());

        assertEquals(401, get("/api/productos", sesionDeLuis).statusCode());
        assertEquals(401, login(luis.usuario(), luis.password()).statusCode());
    }

    @Test
    @DisplayName("PI-05: la contraseña temporal solo permite cambiarla; el cambio abre una sesión normal (HU-36, HU-37)")
    void contrasenaTemporal() throws Exception {
        String admin = token(crearCuenta(ROL_ADMINISTRADOR));
        Cuenta rosa = crearCuenta(ROL_GERENTE);

        HttpResponse<String> restablecer = post("/api/usuarios/" + rosa.id() + "/restablecimiento-password", admin, "");
        assertEquals(200, restablecer.statusCode());
        assertTrue(restablecer.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        Matcher m = Pattern.compile("\"passwordTemporal\"\\s*:\\s*\"([^\"]+)\"").matcher(restablecer.body());
        assertTrue(m.find());
        String temporal = m.group(1);

        // La contraseña anterior ya no sirve; la temporal sí, pero restringida.
        assertEquals(401, login(rosa.usuario(), rosa.password()).statusCode());
        HttpResponse<String> conTemporal = login(rosa.usuario(), temporal);
        assertEquals(200, conTemporal.statusCode());
        assertTrue(conTemporal.body().contains("\"debeCambiarPassword\":true"));
        String sesionTemporal = extraerToken(conTemporal.body());
        assertEquals(403, get("/api/productos", sesionTemporal).statusCode());
        assertEquals(200, get("/api/usuarios/me", sesionTemporal).statusCode());

        // Una contraseña débil se rechaza con el detalle de la política.
        HttpResponse<String> debil = post("/api/auth/cambio-password", sesionTemporal,
                "{\"actual\":\"" + temporal + "\",\"nueva\":\"corta\",\"confirmacion\":\"corta\"}");
        assertEquals(422, debil.statusCode());

        HttpResponse<String> cambio = post("/api/auth/cambio-password", sesionTemporal,
                "{\"actual\":\"" + temporal + "\",\"nueva\":\"Nueva#Clave2026x\",\"confirmacion\":\"Nueva#Clave2026x\"}");
        assertEquals(200, cambio.statusCode());
        String sesionNueva = extraerToken(cambio.body());
        assertEquals(200, get("/api/productos", sesionNueva).statusCode());
        // La sesión temporal quedó cerrada por el cambio.
        assertEquals(401, get("/api/usuarios/me", sesionTemporal).statusCode());
    }

    @Test
    @DisplayName("PI-06: cabeceras de seguridad, identificador de solicitud y estado del servicio (HU-38, HU-41)")
    void cabecerasYSalud() throws Exception {
        HttpResponse<String> salud = get("/actuator/health", null);
        assertEquals(200, salud.statusCode());
        assertTrue(salud.body().contains("UP"));

        HttpResponse<String> r = get("/api/productos", null);
        assertEquals("DENY", r.headers().firstValue("X-Frame-Options").orElse(""));
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertTrue(r.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"));
        assertFalse(r.headers().firstValue("X-Request-Id").orElse("").isBlank());
    }
}

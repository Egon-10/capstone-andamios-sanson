package com.proyecto.microservicio.security;

import com.proyecto.microservicio.config.SolicitudFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP-39 (límite de intentos y cabeceras, HU-38), CP-37 (restricción de la
 * sesión con contraseña temporal, HU-36) y CP-42 (trazabilidad, HU-41).
 */
class FiltrosSprint4Test {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    /** Reloj que avanza a mano. */
    private static final class Reloj extends Clock {
        long ms;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zona) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(ms); }
    }

    private static MockHttpServletRequest login(String ip) {
        MockHttpServletRequest s = new MockHttpServletRequest("POST", "/api/auth/login");
        s.setRemoteAddr(ip);
        return s;
    }

    // --- HU-38 ---

    @Test
    @DisplayName("CP-39: admite hasta el límite por minuto y luego responde 429 con Retry-After")
    void limiteDeIntentos() throws Exception {
        Reloj reloj = new Reloj();
        LimiteSolicitudesFilter filtro = new LimiteSolicitudesFilter(3, 60, reloj);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse r = new MockHttpServletResponse();
            filtro.doFilter(login("10.0.0.1"), r, new MockFilterChain());
            assertEquals(200, r.getStatus());
            reloj.ms += 1000;
        }
        MockHttpServletResponse rechazada = new MockHttpServletResponse();
        filtro.doFilter(login("10.0.0.1"), rechazada, new MockFilterChain());

        assertEquals(429, rechazada.getStatus());
        assertEquals("57", rechazada.getHeader("Retry-After"));
        assertTrue(rechazada.getContentAsString().contains("Demasiados intentos"));
    }

    @Test
    @DisplayName("CP-39: el límite es por origen y se libera al pasar la ventana")
    void limitePorOrigenYVentana() {
        Reloj reloj = new Reloj();
        LimiteSolicitudesFilter filtro = new LimiteSolicitudesFilter(2, 60, reloj);
        assertEquals(0, filtro.registrar("a"));
        assertEquals(0, filtro.registrar("a"));
        assertTrue(filtro.registrar("a") > 0);
        assertEquals(0, filtro.registrar("b"), "otro origen no se ve afectado");
        reloj.ms = 60_000;
        assertEquals(0, filtro.registrar("a"));
    }

    @Test
    @DisplayName("CP-39: el límite solo aplica a las rutas de autenticación")
    void soloRutasDeAutenticacion() throws Exception {
        LimiteSolicitudesFilter filtro = new LimiteSolicitudesFilter(1, 60);
        assertTrue(filtro.shouldNotFilter(new MockHttpServletRequest("GET", "/api/productos")));
        assertTrue(filtro.shouldNotFilter(new MockHttpServletRequest("GET", "/api/auth/login")));
        assertFalse(filtro.shouldNotFilter(new MockHttpServletRequest("POST", "/api/auth/refresh")));
    }

    // --- HU-36 y HU-41 ---

    private static void sesion(boolean temporal) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("7")
                .claim(JwtService.CLAIM_CONTRASENA_TEMPORAL, temporal)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    @DisplayName("CP-37: con contraseña temporal solo se puede cambiarla, ver el perfil o salir")
    void contrasenaTemporal() throws Exception {
        sesion(true);
        ContextoSesionFilter filtro = new ContextoSesionFilter();

        MockHttpServletResponse bloqueada = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest("GET", "/api/productos"), bloqueada, new MockFilterChain());
        assertEquals(403, bloqueada.getStatus());
        assertTrue(bloqueada.getContentAsString().contains("contraseña temporal"));

        MockHttpServletResponse permitida = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest("POST", "/api/auth/cambio-password"), permitida, new MockFilterChain());
        assertEquals(200, permitida.getStatus());
    }

    @Test
    @DisplayName("CP-42: una sesión normal pasa y deja el usuario en el contexto de registro")
    void sesionNormal() throws Exception {
        sesion(false);
        AtomicReference<String> usuario = new AtomicReference<>();
        FilterChain cadena = (s, r) -> usuario.set(MDC.get(SolicitudFilter.CLAVE_USUARIO));

        MockHttpServletResponse r = new MockHttpServletResponse();
        new ContextoSesionFilter().doFilter(new MockHttpServletRequest("GET", "/api/productos"), r, cadena);

        assertEquals(200, r.getStatus());
        assertEquals("7", usuario.get());
    }

    @Test
    @DisplayName("CP-42: cada solicitud recibe un identificador que vuelve en la respuesta")
    void identificadorDeSolicitud() throws Exception {
        AtomicReference<String> visto = new AtomicReference<>();
        FilterChain cadena = (s, r) -> visto.set(MDC.get(SolicitudFilter.CLAVE_SOLICITUD));
        MockHttpServletResponse r = new MockHttpServletResponse();

        new SolicitudFilter().doFilter(new MockHttpServletRequest("GET", "/api/productos"), r, cadena);

        assertNotNull(visto.get());
        assertEquals(visto.get(), r.getHeader(SolicitudFilter.CABECERA));
        assertNull(MDC.get(SolicitudFilter.CLAVE_SOLICITUD), "el contexto se limpia al terminar");
    }

    @Test
    @DisplayName("CP-42: respeta un identificador seguro y descarta uno que podría ensuciar los registros")
    void identificadorRecibido() {
        assertEquals("abc-12345", SolicitudFilter.identificador("abc-12345"));
        assertNotEquals("x\nFALSO", SolicitudFilter.identificador("x\nFALSO"));
        assertEquals(36, SolicitudFilter.identificador(null).length());
    }

    @Test
    @DisplayName("El error escrito desde un filtro es JSON válido aunque el mensaje tenga comillas")
    void jsonEscapado() {
        assertEquals("a \\\"b\\\" \\\\ c\\n", RespuestaJson.escapar("a \"b\" \\ c\n"));
    }
}

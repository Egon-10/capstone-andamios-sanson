package com.proyecto.microservicio.integracion;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base de las pruebas de integración.
 *
 * Levanta la aplicación completa en un puerto libre, conectada al MySQL real
 * con el esquema que dejan las migraciones, y le habla por HTTP como lo haría
 * el navegador: con el filtro de seguridad, los validadores del token, las
 * consultas nativas y las transacciones reales. Nada está simulado.
 *
 * Cada prueba crea sus propios usuarios con una contraseña aleatoria, de modo
 * que no depende de credenciales guardadas en el repositorio ni del orden en
 * que se ejecuten las demás.
 */
@Tag("integracion")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integracion")
abstract class IntegracionBase {

    protected static final long ROL_ADMINISTRADOR = 1;
    protected static final long ROL_ENCARGADO = 2;
    protected static final long ROL_GERENTE = 3;

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Autowired
    private Environment entorno;

    @Autowired
    private DataSource dataSource;

    protected final HttpClient http = HttpClient.newHttpClient();
    protected final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);

    protected JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    protected String url(String ruta) {
        return "http://127.0.0.1:" + entorno.getProperty("local.server.port") + ruta;
    }

    /** Cuenta de prueba creada para esta prueba. */
    protected record Cuenta(long id, String usuario, String password) {
    }

    protected Cuenta crearCuenta(long rolId) {
        String usuario = "it_" + UUID.randomUUID().toString().substring(0, 8);
        String password = "Prueba#" + Long.toHexString(ALEATORIO.nextLong()) + "Z9";
        String documento = String.valueOf(10_000_000 + ALEATORIO.nextInt(89_999_999));
        jdbc().update("""
                INSERT INTO usuarios (nombre, apellidos, tipo_documento, numero_documento, correo, nombre_usuario,
                                      password, area, estado, fecha_creacion, rol_id)
                VALUES (?, 'Integracion', 'DNI', ?, ?, ?, ?, 'LOGISTICA', 'ACTIVO', NOW(), ?)
                """, "Prueba", documento, usuario + "@prueba.pe", usuario, bcrypt.encode(password), rolId);
        Long id = jdbc().queryForObject("SELECT id FROM usuarios WHERE nombre_usuario = ?", Long.class, usuario);
        return new Cuenta(id, usuario, password);
    }

    protected HttpResponse<String> login(String usuario, String password) throws IOException, InterruptedException {
        return post("/api/auth/login", null,
                "{\"correo\":\"" + usuario + "\",\"password\":\"" + password + "\"}");
    }

    protected String token(Cuenta c) throws IOException, InterruptedException {
        HttpResponse<String> r = login(c.usuario(), c.password());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("No se pudo iniciar sesión: " + r.statusCode() + " " + r.body());
        }
        return extraerToken(r.body());
    }

    protected static String extraerToken(String cuerpo) {
        Matcher m = TOKEN.matcher(cuerpo);
        if (!m.find()) {
            throw new IllegalStateException("La respuesta no trae token: " + cuerpo);
        }
        return m.group(1);
    }

    protected HttpResponse<String> get(String ruta, String token) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(ruta))).GET();
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    protected HttpResponse<byte[]> getBytes(String ruta, String token) throws IOException, InterruptedException {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url(ruta))).GET()
                .header("Authorization", "Bearer " + token).build();
        return http.send(r, HttpResponse.BodyHandlers.ofByteArray());
    }

    protected HttpResponse<String> post(String ruta, String token, String json) throws IOException, InterruptedException {
        return enviar("POST", ruta, token, json);
    }

    protected HttpResponse<String> patch(String ruta, String token, String json) throws IOException, InterruptedException {
        return enviar("PATCH", ruta, token, json);
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String token, String json)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(ruta)))
                .header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString(json == null ? "" : json));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}

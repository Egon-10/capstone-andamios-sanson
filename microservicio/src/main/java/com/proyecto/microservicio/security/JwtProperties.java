package com.proyecto.microservicio.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Configuración externalizada de la sesión (HU-01, HU-03, HU-09).
 * Los valores se leen de application.properties o de variables de entorno.
 */
@Component
public class JwtProperties {

    private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);
    public static final String EMISOR = "inventario-andamios-sanson";

    private final SecretKey clave;
    private final Duration duracionAcceso;
    private final Duration inactividadMaxima;

    public JwtProperties(
            @Value("${app.jwt.secret:}") String secreto,
            @Value("${app.jwt.access-ttl-minutos:15}") long minutosAcceso,
            @Value("${app.jwt.inactividad-minutos:30}") long minutosInactividad) {

        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            log.warn("app.jwt.secret no está definido o tiene menos de 32 caracteres: se genera una clave "
                    + "temporal y las sesiones se cerrarán al reiniciar el servidor. Configure JWT_SECRET.");
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
        }
        this.clave = new SecretKeySpec(bytes, "HmacSHA256");
        this.duracionAcceso = Duration.ofMinutes(minutosAcceso);
        this.inactividadMaxima = Duration.ofMinutes(minutosInactividad);
    }

    public SecretKey getClave() {
        return clave;
    }

    public Duration getDuracionAcceso() {
        return duracionAcceso;
    }

    /** Tiempo sin actividad tras el cual el token de renovación deja de ser válido. */
    public Duration getInactividadMaxima() {
        return inactividadMaxima;
    }
}

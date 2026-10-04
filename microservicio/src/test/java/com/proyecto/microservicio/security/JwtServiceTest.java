package com.proyecto.microservicio.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.proyecto.microservicio.config.SecurityConfig;
import com.proyecto.microservicio.model.Rol;
import com.proyecto.microservicio.model.Roles;
import com.proyecto.microservicio.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CP-03 y CP-09: emisión, validación y expiración del token de acceso. */
class JwtServiceTest {

    private JwtProperties propiedades;
    private JwtEncoder encoder;
    private NimbusJwtDecoder decoder;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        propiedades = new JwtProperties("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres", 15, 30);
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(propiedades.getClave()));
        decoder = NimbusJwtDecoder.withSecretKey(propiedades.getClave()).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtProperties.EMISOR));

        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setNombre("Ana");
        usuario.setRol(new Rol(1L, Roles.ADMINISTRADOR));
    }

    @Test
    @DisplayName("El token emitido contiene el usuario, el rol y un identificador único")
    void tokenContieneUsuarioYRol() {
        String token = new JwtService(encoder, propiedades).generarTokenAcceso(usuario);

        Jwt jwt = decoder.decode(token);

        assertEquals("7", jwt.getSubject());
        assertEquals(List.of("ADMINISTRADOR"), jwt.getClaimAsStringList(JwtService.CLAIM_ROLES));
        assertNotNull(jwt.getId());
        assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

    @Test
    @DisplayName("CP-09: un token vencido es rechazado")
    void tokenVencidoEsRechazado() {
        Clock hace20Minutos = Clock.fixed(Instant.now().minus(Duration.ofMinutes(20)), ZoneOffset.UTC);
        String token = new JwtService(encoder, propiedades, hace20Minutos).generarTokenAcceso(usuario);

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    @DisplayName("Un token firmado con otra clave es rechazado")
    void tokenConOtraFirmaEsRechazado() {
        JwtProperties otra = new JwtProperties("otra-clave-distinta-tambien-de-mas-de-32-caracteres", 15, 30);
        JwtEncoder encoderAjeno = new NimbusJwtEncoder(new ImmutableSecret<>(otra.getClave()));
        String token = new JwtService(encoderAjeno, otra).generarTokenAcceso(usuario);

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    @DisplayName("CP-05: el rol del token se convierte en la autoridad ROLE_ que exige el servidor")
    void rolSeConvierteEnAutoridad() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject("7")
                .claim(JwtService.CLAIM_ROLES, List.of("ENCARGADO"))
                .build();

        AbstractAuthenticationToken auth = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);

        assertNotNull(auth);
        List<String> autoridades = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertEquals(List.of("ROLE_ENCARGADO"), autoridades);
    }

    @Test
    @DisplayName("Los nombres de rol se normalizan sin tildes ni espacios")
    void normalizaNombreDeRol() {
        assertEquals("ENCARGADO_DE_ALMACEN", Roles.normalizar("Encargado de almacén"));
    }

    @Test
    @DisplayName("Sin secreto configurado se genera una clave temporal válida")
    void secretoCortoGeneraClaveTemporal() {
        JwtProperties sinSecreto = new JwtProperties("", 15, 30);
        assertEquals(32, sinSecreto.getClave().getEncoded().length);
    }
}

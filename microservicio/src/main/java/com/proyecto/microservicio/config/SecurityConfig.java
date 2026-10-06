package com.proyecto.microservicio.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.proyecto.microservicio.model.Roles;
import com.proyecto.microservicio.repository.TokenRevocadoRepository;
import com.proyecto.microservicio.security.JwtProperties;
import com.proyecto.microservicio.security.JwtService;
import com.proyecto.microservicio.security.TokenNoRevocadoValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * HU-03, HU-04 y HU-05: autenticación sin estado con JWT y autorización por rol
 * aplicada en el servidor (SEG-AP-01, SEG-AP-02).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = Roles.ADMINISTRADOR;
    private static final String GERENTE = Roles.GERENTE;

    @Bean
    public SecurityFilterChain cadenaDeFiltros(HttpSecurity http, JwtAuthenticationConverter convertidor) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/error").permitAll()

                // Perfil propio: cualquier usuario autenticado
                .requestMatchers("/api/usuarios/me").authenticated()

                // Administración de usuarios y roles
                .requestMatchers("/api/usuarios/**", "/api/roles/**").hasRole(ADMIN)

                // Auditoría: consulta restringida; el registro lo hace el propio servidor
                .requestMatchers(HttpMethod.GET, "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                .requestMatchers(HttpMethod.GET, "/api/reportes/usuarios", "/api/reportes/auditoria").hasAnyRole(ADMIN, GERENTE)

                // Catálogo: lectura para todos; escritura según rol
                .requestMatchers(HttpMethod.DELETE, "/api/productos/**", "/api/proveedores/**", "/api/categorias/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/categorias/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/categorias/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/productos/**", "/api/proveedores/**").hasAnyRole(ADMIN, GERENTE)
                .requestMatchers(HttpMethod.PUT, "/api/productos/**", "/api/proveedores/**").hasAnyRole(ADMIN, GERENTE)

                // Movimientos: registrar (todos los roles), editar (admin y gerente), eliminar (admin)
                .requestMatchers(HttpMethod.PUT, "/api/movimientos/**").hasAnyRole(ADMIN, GERENTE)
                .requestMatchers(HttpMethod.DELETE, "/api/movimientos/**").hasRole(ADMIN)

                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor)));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName(JwtService.CLAIM_ROLES);
        autoridades.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties propiedades) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(propiedades.getClave()));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties propiedades, TokenRevocadoRepository revocados) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(propiedades.getClave())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JwtProperties.EMISOR),
                new TokenNoRevocadoValidator(revocados)));
        return decoder;
    }

    /** HU-04: contraseñas con hash BCrypt (SEG-BD-01). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CORS centralizado: reemplaza los @CrossOrigin("*") de cada controlador. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.origenes:http://localhost:4200}") String origenes) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}

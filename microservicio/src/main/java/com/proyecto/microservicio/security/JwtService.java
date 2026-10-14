package com.proyecto.microservicio.security;

import com.proyecto.microservicio.model.Roles;
import com.proyecto.microservicio.model.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HU-03: emisión del token de acceso firmado (HS256) con el rol del usuario. */
@Service
public class JwtService {

    public static final String CLAIM_ROLES = "roles";

    /**
     * HU-36: marca el token de una sesión abierta con contraseña temporal.
     * Mientras la tenga, el servidor solo admite el cambio de contraseña.
     */
    public static final String CLAIM_CONTRASENA_TEMPORAL = "pwd_temporal";

    private final JwtEncoder encoder;
    private final JwtProperties propiedades;
    private final Clock reloj;

    @Autowired
    public JwtService(JwtEncoder encoder, JwtProperties propiedades) {
        this(encoder, propiedades, Clock.systemUTC());
    }

    JwtService(JwtEncoder encoder, JwtProperties propiedades, Clock reloj) {
        this.encoder = encoder;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public String generarTokenAcceso(Usuario usuario) {
        Instant ahora = reloj.instant();
        String rol = usuario.getRol() == null ? "" : Roles.normalizar(usuario.getRol().getNombre());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtProperties.EMISOR)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(propiedades.getDuracionAcceso()))
                .subject(String.valueOf(usuario.getId()))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_ROLES, List.of(rol))
                .claim("nombre", usuario.getNombre() == null ? "" : usuario.getNombre())
                .claim(CLAIM_CONTRASENA_TEMPORAL, usuario.isDebeCambiarPassword())
                .build();

        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    public long segundosDeVigencia() {
        return propiedades.getDuracionAcceso().toSeconds();
    }
}

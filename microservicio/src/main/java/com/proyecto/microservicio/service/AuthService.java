package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.LoginRequest;
import com.proyecto.microservicio.dto.LoginResponse;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import com.proyecto.microservicio.model.TokenRevocado;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.TokenRevocadoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.JwtProperties;
import com.proyecto.microservicio.security.JwtService;
import com.proyecto.microservicio.security.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * HU-03: inicio de sesión con token y sesión en servidor.
 * HU-08: cierre de sesión con invalidación efectiva del token.
 * HU-09: renovación de la sesión mientras el usuario esté activo.
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final TokenRevocadoRepository revocados;
    private final AuditoriaService auditoria;
    private final JwtProperties propiedades;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService,
                       RefreshTokenService refreshTokens, TokenRevocadoRepository revocados,
                       AuditoriaService auditoria, JwtProperties propiedades) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.revocados = revocados;
        this.auditoria = auditoria;
        this.propiedades = propiedades;
    }

    @Transactional
    public LoginResponse login(LoginRequest solicitud) {
        String identificador = solicitud.correo().trim();
        Optional<Usuario> encontrado = identificador.contains("@")
                ? usuarios.findByCorreoIgnoreCase(identificador)
                : usuarios.findByNombreUsuario(identificador);

        Usuario usuario = encontrado
                .filter(Usuario::estaActivo)
                .filter(u -> u.getPassword() != null && encoder.matches(solicitud.password(), u.getPassword()))
                .orElseThrow(CredencialesInvalidasException::new);

        auditoria.registrar("INICIO DE SESIÓN", usuario.getId());
        return emitirSesion(usuario);
    }

    @Transactional
    public LoginResponse renovar(String refreshToken) {
        Usuario usuario = refreshTokens.consumir(refreshToken);
        if (!usuario.estaActivo()) {
            throw new CredencialesInvalidasException("La cuenta está inactiva");
        }
        return emitirSesion(usuario);
    }

    /** Revoca el token de renovación y registra el identificador del token de acceso actual. */
    @Transactional
    public void logout(String refreshToken, String jtiAcceso, Instant expiracionAcceso, Long usuarioId) {
        refreshTokens.revocar(refreshToken);
        if (jtiAcceso != null && expiracionAcceso != null) {
            revocados.save(new TokenRevocado(jtiAcceso, expiracionAcceso));
        }
        auditoria.registrar("CIERRE DE SESIÓN", usuarioId);
    }

    private LoginResponse emitirSesion(Usuario usuario) {
        return new LoginResponse(
                jwtService.generarTokenAcceso(usuario),
                refreshTokens.emitir(usuario),
                "Bearer",
                jwtService.segundosDeVigencia(),
                propiedades.getInactividadMaxima().toSeconds(),
                UsuarioResponse.from(usuario));
    }
}

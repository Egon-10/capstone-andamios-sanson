package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.LoginRequest;
import com.proyecto.microservicio.dto.LoginResponse;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.TokenRevocado;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.TokenRevocadoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.JwtProperties;
import com.proyecto.microservicio.security.JwtService;
import com.proyecto.microservicio.security.OrigenSolicitud;
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
 * HU-32: cada intento de inicio de sesión queda en la bitácora de accesos.
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
    private final AccesoService accesos;

    /**
     * Hash BCrypt de una contraseña que nadie usa. Se compara contra él cuando
     * la cuenta no existe, para que esa respuesta tarde lo mismo que la de una
     * contraseña incorrecta y el tiempo no delate qué cuentas existen.
     */
    private static final String HASH_SENUELO = "$2a$10$YpbjJfmOnbW8jzkLP2T.uuoMFDTzdDUwesqB6UNhgaffjCyS7wz86";

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService,
                       RefreshTokenService refreshTokens, TokenRevocadoRepository revocados,
                       AuditoriaService auditoria, JwtProperties propiedades, AccesoService accesos) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.revocados = revocados;
        this.auditoria = auditoria;
        this.propiedades = propiedades;
        this.accesos = accesos;
    }

    @Transactional
    public LoginResponse login(LoginRequest solicitud) {
        return login(solicitud, OrigenSolicitud.DESCONOCIDO);
    }

    /**
     * Valida las credenciales y abre la sesión. Al usuario se le responde
     * siempre el mismo mensaje genérico; la causa real del rechazo queda
     * solo en la bitácora de accesos, que consulta el administrador.
     */
    @Transactional
    public LoginResponse login(LoginRequest solicitud, OrigenSolicitud origen) {
        String identificador = solicitud.correo().trim();
        Optional<Usuario> encontrado = identificador.contains("@")
                ? usuarios.findByCorreoIgnoreCase(identificador)
                : usuarios.findByNombreUsuario(identificador);

        if (encontrado.isEmpty()) {
            encoder.matches(solicitud.password(), HASH_SENUELO);
            throw rechazo(identificador, null, "Cuenta inexistente", origen);
        }

        Usuario usuario = encontrado.get();
        if (!usuario.estaActivo()) {
            throw rechazo(identificador, usuario, "Cuenta inactiva", origen);
        }
        if (usuario.getPassword() == null || !encoder.matches(solicitud.password(), usuario.getPassword())) {
            throw rechazo(identificador, usuario, "Contraseña incorrecta", origen);
        }

        auditoria.registrar("INICIO DE SESIÓN", usuario.getId());
        accesos.registrar(identificador, usuario, Acceso.EXITOSO, null, origen);
        return emitirSesion(usuario);
    }

    private CredencialesInvalidasException rechazo(String identificador, Usuario usuario, String motivo,
                                                   OrigenSolicitud origen) {
        accesos.registrar(identificador, usuario, Acceso.FALLIDO, motivo, origen);
        return new CredencialesInvalidasException();
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

package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.LoginRequest;
import com.proyecto.microservicio.dto.LoginResponse;
import com.proyecto.microservicio.dto.UsuarioResponse;
import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.CambioPasswordRequest;
import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import com.proyecto.microservicio.exception.CuentaBloqueadaException;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.TokenRevocado;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.TokenRevocadoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;
import com.proyecto.microservicio.security.JwtProperties;
import com.proyecto.microservicio.security.JwtService;
import com.proyecto.microservicio.security.OrigenSolicitud;
import com.proyecto.microservicio.security.PoliticaContrasena;
import com.proyecto.microservicio.security.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-03: inicio de sesión con token y sesión en servidor.
 * HU-08: cierre de sesión con invalidación efectiva del token.
 * HU-09: renovación de la sesión mientras el usuario esté activo.
 * HU-32: cada intento de inicio de sesión queda en la bitácora de accesos.
 * HU-35: bloqueo temporal tras intentos fallidos consecutivos.
 * HU-37: cambio de la contraseña propia con política de complejidad.
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
    private final BloqueoCuentaService bloqueo;

    /**
     * Hash de una contraseña aleatoria que nadie conoce. Se compara contra él
     * cuando la cuenta no existe, para que esa respuesta tarde lo mismo que la
     * de una contraseña incorrecta y el tiempo no delate qué cuentas existen.
     * Se genera al arrancar en lugar de escribirse en el código, para que no
     * haya ningún hash fijo en el repositorio.
     */
    private final String hashSenuelo;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService,
                       RefreshTokenService refreshTokens, TokenRevocadoRepository revocados,
                       AuditoriaService auditoria, JwtProperties propiedades, AccesoService accesos,
                       BloqueoCuentaService bloqueo) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.revocados = revocados;
        this.auditoria = auditoria;
        this.propiedades = propiedades;
        this.accesos = accesos;
        this.bloqueo = bloqueo;
        this.hashSenuelo = encoder.encode(UUID.randomUUID().toString());
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
            encoder.matches(solicitud.password(), hashSenuelo);
            throw rechazo(identificador, null, "Cuenta inexistente", origen);
        }

        Usuario usuario = encontrado.get();
        if (!usuario.estaActivo()) {
            throw rechazo(identificador, usuario, "Cuenta inactiva", origen);
        }
        // HU-35: el bloqueo se comprueba antes que la contraseña, para que
        // durante el bloqueo acertarla no dé ninguna señal a quien adivina.
        LocalDateTime ahora = ZonaHoraria.ahora();
        if (usuario.estaBloqueado(ahora)) {
            accesos.registrar(identificador, usuario, Acceso.BLOQUEADO, "Cuenta bloqueada", origen);
            throw new CuentaBloqueadaException(
                    BloqueoCuentaService.minutosRestantes(usuario.getBloqueadoHasta(), ahora));
        }
        if (usuario.getPassword() == null || !encoder.matches(solicitud.password(), usuario.getPassword())) {
            BloqueoCuentaService.Fallo fallo = bloqueo.registrarFallo(usuario.getId());
            if (fallo.bloqueo()) {
                accesos.registrar(identificador, usuario, Acceso.BLOQUEADO,
                        "Contraseña incorrecta; cuenta bloqueada tras " + fallo.intentos() + " intentos", origen);
                throw new CuentaBloqueadaException(
                        BloqueoCuentaService.minutosRestantes(fallo.bloqueadoHasta(), ahora));
            }
            throw rechazo(identificador, usuario,
                    "Contraseña incorrecta (intento " + fallo.intentos() + " de " + fallo.maximo() + ")", origen);
        }

        bloqueo.reiniciar(usuario);
        auditoria.registrar("INICIO DE SESIÓN", usuario.getId());
        accesos.registrar(identificador, usuario, Acceso.EXITOSO, null, origen);
        return emitirSesion(usuario);
    }

    /**
     * HU-37: cambia la contraseña propia.
     *
     * Exige la actual, aplica la política de complejidad y no admite repetir la
     * misma. Al cambiarla se cierran todas las demás sesiones abiertas de la
     * cuenta (si alguien más la conocía, deja de tener acceso) y se abre una
     * sesión nueva para quien la cambió, que sigue trabajando sin volver a
     * ingresar. Si la contraseña era temporal (HU-36), deja de serlo.
     */
    @Transactional
    public LoginResponse cambiarPassword(Long usuarioId, CambioPasswordRequest s) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(CredencialesInvalidasException::new);
        if (u.getPassword() == null || !encoder.matches(s.actual(), u.getPassword())) {
            throw new ReglaNegocioException("actual", "La contraseña actual no es correcta");
        }
        if (!s.nueva().equals(s.confirmacion())) {
            throw new ReglaNegocioException("confirmacion", "La nueva contraseña y su confirmación no coinciden");
        }
        if (encoder.matches(s.nueva(), u.getPassword())) {
            throw new ReglaNegocioException("nueva", "La nueva contraseña debe ser distinta de la actual");
        }
        PoliticaContrasena.exigir("nueva", s.nueva(), u.getNombreUsuario(), u.getNumeroDocumento());

        LocalDateTime ahora = ZonaHoraria.ahora().truncatedTo(ChronoUnit.SECONDS);
        u.setPassword(encoder.encode(s.nueva()));
        u.setDebeCambiarPassword(false);
        u.setFechaCambioPassword(ahora);
        u.setSesionesValidasDesde(ahora);
        usuarios.save(u);
        int cerradas = refreshTokens.revocarTodos(u.getId());

        auditoria.registrar("PASSWORD_CAMBIADA",
                "El usuario cambió su contraseña y se cerraron " + cerradas + " sesiones abiertas", u.getId());
        return emitirSesion(u);
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

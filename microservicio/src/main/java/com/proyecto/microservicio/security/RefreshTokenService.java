package com.proyecto.microservicio.security;

import com.proyecto.microservicio.exception.CredencialesInvalidasException;
import com.proyecto.microservicio.model.RefreshToken;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * HU-03 y HU-09: tokens de renovación con rotación. Cada token vale una sola vez
 * y vence tras el periodo de inactividad configurado; si el usuario no realiza
 * ninguna acción en ese periodo, la sesión expira.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repositorio;
    private final JwtProperties propiedades;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repositorio, JwtProperties propiedades) {
        this(repositorio, propiedades, Clock.systemDefaultZone());
    }

    RefreshTokenService(RefreshTokenRepository repositorio, JwtProperties propiedades, Clock reloj) {
        this.repositorio = repositorio;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** Emite un token nuevo y devuelve su valor en claro (solo se almacena el hash). */
    @Transactional
    public String emitir(Usuario usuario) {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime vence = LocalDateTime.now(reloj).plus(propiedades.getInactividadMaxima());
        repositorio.save(new RefreshToken(HashUtil.sha256(valor), usuario, vence));
        return valor;
    }

    /** Valida el token, lo revoca y devuelve el usuario al que pertenece. */
    @Transactional
    public Usuario consumir(String valor) {
        RefreshToken token = repositorio.findByTokenHash(HashUtil.sha256(valor))
                .orElseThrow(() -> new CredencialesInvalidasException("La sesión expiró. Inicie sesión nuevamente."));

        if (!token.estaVigente(LocalDateTime.now(reloj))) {
            throw new CredencialesInvalidasException("La sesión expiró. Inicie sesión nuevamente.");
        }
        token.revocar();
        repositorio.save(token);
        return token.getUsuario();
    }

    /** HU-31: revoca todos los tokens de renovación del usuario. Devuelve cuántos había vigentes. */
    @Transactional
    public int revocarTodos(Long usuarioId) {
        return repositorio.revocarTodosDe(usuarioId);
    }

    /** Revoca el token sin emitir otro (cierre de sesión). Ignora tokens inexistentes. */
    @Transactional
    public void revocar(String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        repositorio.findByTokenHash(HashUtil.sha256(valor)).ifPresent(t -> {
            t.revocar();
            repositorio.save(t);
        });
    }
}

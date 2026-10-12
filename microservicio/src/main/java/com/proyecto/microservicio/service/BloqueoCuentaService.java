package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * HU-35: bloqueo temporal de la cuenta tras intentos fallidos consecutivos.
 *
 * Cada fallo se guarda en una transacción propia: el inicio de sesión fallido
 * termina en una excepción que deshace su transacción, y si el contador viajara
 * en ella nunca llegaría a sumar. Al alcanzar el máximo, la cuenta queda
 * bloqueada hasta una hora de fin y se levanta sola; el contador vuelve a cero
 * para que, vencido el bloqueo, haya de nuevo el mismo margen.
 */
@Service
public class BloqueoCuentaService {

    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final int intentosMaximos;
    private final Duration duracionBloqueo;

    public BloqueoCuentaService(UsuarioRepository usuarios, AuditoriaService auditoria,
                                @Value("${app.seguridad.intentos-maximos:5}") int intentosMaximos,
                                @Value("${app.seguridad.minutos-bloqueo:15}") long minutosBloqueo) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.intentosMaximos = intentosMaximos;
        this.duracionBloqueo = Duration.ofMinutes(minutosBloqueo);
    }

    /** Resultado de registrar un fallo: cuántos van y, si se bloqueó, hasta cuándo. */
    public record Fallo(int intentos, int maximo, LocalDateTime bloqueadoHasta) {
        public boolean bloqueo() {
            return bloqueadoHasta != null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Fallo registrarFallo(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        int intentos = u.getIntentosFallidos() + 1;
        if (intentos >= intentosMaximos) {
            LocalDateTime hasta = ZonaHoraria.ahora().plus(duracionBloqueo);
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(hasta);
            usuarios.save(u);
            auditoria.registrar("CUENTA_BLOQUEADA",
                    "La cuenta " + u.getNombreUsuario() + " se bloqueó hasta las " + hasta.toLocalTime().withNano(0)
                            + " tras " + intentos + " intentos fallidos consecutivos",
                    null);
            return new Fallo(intentos, intentosMaximos, hasta);
        }
        u.setIntentosFallidos(intentos);
        usuarios.save(u);
        return new Fallo(intentos, intentosMaximos, null);
    }

    /** Un acceso correcto reinicia el contador. */
    @Transactional
    public void reiniciar(Usuario u) {
        if (u.getIntentosFallidos() != 0 || u.getBloqueadoHasta() != null) {
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(null);
            usuarios.save(u);
        }
    }

    /** Minutos que faltan para que se levante el bloqueo, redondeado hacia arriba. */
    public static long minutosRestantes(LocalDateTime hasta, LocalDateTime ahora) {
        long segundos = Math.max(Duration.between(ahora.atZone(ZonaHoraria.NEGOCIO), hasta.atZone(ZonaHoraria.NEGOCIO)).getSeconds(), 0);
        return Math.max((segundos + 59) / 60, 1);
    }
}

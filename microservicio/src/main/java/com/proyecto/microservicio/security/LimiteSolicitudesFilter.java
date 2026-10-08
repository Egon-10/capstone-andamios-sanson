package com.proyecto.microservicio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HU-38: límite de intentos por origen sobre las rutas de autenticación.
 *
 * El bloqueo de cuenta (HU-35) protege a una cuenta; este límite protege al
 * servidor de quien prueba muchas cuentas desde el mismo equipo, o muchas
 * veces la misma. Se usa una ventana deslizante en memoria: el sistema corre
 * en un único servidor y no necesita un almacén compartido. Si se escalara a
 * varias instancias, el límite debería pasar al proxy o a un almacén común.
 */
public class LimiteSolicitudesFilter extends OncePerRequestFilter {

    static final Set<String> RUTAS = Set.of("/api/auth/login", "/api/auth/refresh", "/api/auth/cambio-password");
    private static final int LIMPIAR_CADA = 500;

    private final int maximo;
    private final long ventanaMs;
    private final Clock reloj;
    private final Map<String, Deque<Long>> intentos = new ConcurrentHashMap<>();
    private int solicitudesDesdeLimpieza;

    public LimiteSolicitudesFilter(int maximo, long ventanaSegundos) {
        this(maximo, ventanaSegundos, Clock.systemUTC());
    }

    LimiteSolicitudesFilter(int maximo, long ventanaSegundos, Clock reloj) {
        this.maximo = maximo;
        this.ventanaMs = ventanaSegundos * 1000;
        this.reloj = reloj;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest solicitud) {
        return !"POST".equals(solicitud.getMethod()) || !RUTAS.contains(solicitud.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        long espera = registrar(solicitud.getRemoteAddr() + " " + solicitud.getRequestURI());
        if (espera > 0) {
            respuesta.setHeader("Retry-After", String.valueOf(espera));
            RespuestaJson.error(respuesta, 429, "Too Many Requests",
                    "Demasiados intentos seguidos. Espere " + espera + " segundos e intente de nuevo.");
            return;
        }
        cadena.doFilter(solicitud, respuesta);
    }

    /** Registra el intento y devuelve cuántos segundos hay que esperar (0 si se admite). */
    long registrar(String clave) {
        long ahora = reloj.millis();
        limpiarDeVezEnCuando(ahora);
        long[] espera = {0};
        // compute es atómico por clave: dos intentos simultáneos del mismo
        // origen no pueden leer la misma cuenta y pasar los dos.
        intentos.compute(clave, (k, marcas) -> {
            Deque<Long> lista = marcas == null ? new ArrayDeque<>() : marcas;
            while (!lista.isEmpty() && ahora - lista.peekFirst() >= ventanaMs) {
                lista.pollFirst();
            }
            if (lista.size() >= maximo) {
                long liberaEn = lista.peekFirst() + ventanaMs - ahora;
                espera[0] = Math.max((liberaEn + 999) / 1000, 1);
            } else {
                lista.addLast(ahora);
            }
            return lista;
        });
        return espera[0];
    }

    /** Evita que el mapa crezca sin límite con orígenes que ya no vuelven. */
    private synchronized void limpiarDeVezEnCuando(long ahora) {
        if (++solicitudesDesdeLimpieza < LIMPIAR_CADA) {
            return;
        }
        solicitudesDesdeLimpieza = 0;
        for (String clave : intentos.keySet()) {
            intentos.computeIfPresent(clave, (k, marcas) -> {
                Long ultima = marcas.peekLast();
                return ultima == null || ahora - ultima >= ventanaMs ? null : marcas;
            });
        }
    }
}

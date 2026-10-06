package com.proyecto.microservicio.config;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Zona horaria del negocio.
 *
 * LocalDateTime.now() sin argumentos toma la zona del servidor donde corra la
 * aplicacion. En la PC de desarrollo es Lima, pero un runner de la CI o un
 * servidor en la nube suelen estar en UTC, cinco horas adelante: un
 * movimiento registrado a las 21:00 quedaria con fecha del dia siguiente, y
 * los filtros del kardex y de los reportes por dia lo pondrian en el dia
 * equivocado. La empresa opera en Peru, asi que todas las fechas se toman en
 * la hora de Lima sin importar donde este el servidor.
 */
public final class ZonaHoraria {

    public static final ZoneId NEGOCIO = ZoneId.of("America/Lima");

    private ZonaHoraria() {
    }

    /** Fecha y hora actuales en la zona del negocio. */
    public static LocalDateTime ahora() {
        return LocalDateTime.now(NEGOCIO);
    }
}

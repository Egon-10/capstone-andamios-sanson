package com.proyecto.microservicio.model;

/** HU-23: movimientos vigentes registrados desde el inicio del dia. */
public interface MovimientosDelDiaDTO {

    Long getTotal();

    Long getEntradas();

    Long getSalidas();
}

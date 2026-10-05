package com.proyecto.microservicio.model;

import java.time.LocalDateTime;

/** HU-22: totales del histórico de un producto. */
public interface ResumenProductoDTO {

    Long getMovimientos();

    Long getEntradas();

    Long getSalidas();

    Integer getUnidadesIngresadas();

    Integer getUnidadesRetiradas();

    LocalDateTime getUltimoMovimiento();
}

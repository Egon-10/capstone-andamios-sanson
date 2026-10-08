package com.proyecto.microservicio.model;

import java.math.BigDecimal;

/** HU-23: agregados del catalogo activo para el panel de indicadores. */
public interface IndicadoresProductoDTO {

    Long getProductosActivos();

    Long getUnidades();

    BigDecimal getValor();

    Long getPorReponer();

    Long getSinStock();
}

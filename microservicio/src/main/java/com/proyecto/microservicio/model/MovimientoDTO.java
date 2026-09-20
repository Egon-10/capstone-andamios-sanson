package com.proyecto.microservicio.model;

import java.time.LocalDateTime;

public interface MovimientoDTO {

    Long getId();

    String getTipo();

    Integer getCantidad();

    LocalDateTime getFecha();

    String getProducto();

    String getUsuario();
}
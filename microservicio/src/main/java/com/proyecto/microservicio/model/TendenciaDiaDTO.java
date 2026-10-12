package com.proyecto.microservicio.model;

/**
 * HU-24: movimientos de un dia. La fecha llega como texto AAAA-MM-DD para no
 * depender de como el controlador JDBC convierte el tipo DATE.
 */
public interface TendenciaDiaDTO {

    String getDia();

    Long getEntradas();

    Long getSalidas();

    Long getUnidadesEntrada();

    Long getUnidadesSalida();
}

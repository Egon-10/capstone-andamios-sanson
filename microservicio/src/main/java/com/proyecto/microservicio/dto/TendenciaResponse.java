package com.proyecto.microservicio.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * HU-24: tendencia de movimientos en un periodo.
 *
 * La serie trae un elemento por cada dia del periodo, incluidos los dias sin
 * movimientos con valor cero. Si se omitieran, el grafico uniria dos dias
 * separados como si fueran consecutivos y la tendencia se leeria mal.
 */
public record TendenciaResponse(
        LocalDate desde,
        LocalDate hasta,
        long totalEntradas,
        long totalSalidas,
        long unidadesEntrada,
        long unidadesSalida,
        List<Dia> dias) {

    public record Dia(
            LocalDate fecha,
            long entradas,
            long salidas,
            long unidadesEntrada,
            long unidadesSalida) {
    }
}

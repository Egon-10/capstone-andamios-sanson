package com.proyecto.microservicio.dto;

/**
 * HU-25: producto en situacion de stock critico.
 *
 * El nivel ordena la atencion: AGOTADO cuando no queda stock, CRITICO cuando
 * queda la mitad del umbral o menos, y BAJO cuando se alcanzo el umbral pero
 * todavia hay margen. Reponer hasta el maximo es la cantidad a pedir si el
 * producto tiene un stock maximo definido.
 */
public record StockCriticoResponse(
        Long productoId,
        String sku,
        String producto,
        String categoria,
        int stock,
        int umbral,
        int faltanteHastaUmbral,
        Integer reponerHastaMaximo,
        String nivel) {
}

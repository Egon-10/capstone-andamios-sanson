package com.proyecto.microservicio.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * HU-21: página de resultados.
 *
 * Se devuelve este registro en lugar del Page de Spring Data porque la
 * serialización de Page no es un contrato estable entre versiones: su forma ha
 * cambiado y el cliente quedaría atado a un detalle interno del framework.
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        boolean primera,
        boolean ultima) {

    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> convertir) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(convertir).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isFirst(),
                pagina.isLast());
    }
}

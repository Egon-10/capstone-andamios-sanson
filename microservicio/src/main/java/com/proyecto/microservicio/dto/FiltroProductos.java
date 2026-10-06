package com.proyecto.microservicio.dto;

/**
 * HU-21: filtros y paginacion de la busqueda de productos, tal como llegan
 * por la URL. Se agrupan en un registro para no pasar nueve parametros
 * sueltos entre el controlador y el servicio.
 */
public record FiltroProductos(
        String texto,
        Long categoriaId,
        Long proveedorId,
        boolean soloActivos,
        boolean porReponer,
        int pagina,
        int tamano,
        String orden,
        String direccion) {
}

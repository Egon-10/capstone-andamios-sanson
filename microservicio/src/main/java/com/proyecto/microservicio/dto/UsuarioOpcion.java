package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.model.Usuario;

/**
 * Usuario reducido a lo necesario para elegirlo en un filtro (por ejemplo, la
 * bitácora de auditoría). No lleva datos de contacto ni documento.
 */
public record UsuarioOpcion(Long id, String nombre, String nombreUsuario) {

    public static UsuarioOpcion de(Usuario u) {
        return new UsuarioOpcion(u.getId(), u.getNombreCompleto(), u.getNombreUsuario());
    }
}

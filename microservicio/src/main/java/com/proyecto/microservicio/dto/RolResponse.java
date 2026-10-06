package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.model.Rol;

public record RolResponse(Long id, String nombre) {

    public static RolResponse from(Rol rol) {
        return rol == null ? null : new RolResponse(rol.getId(), rol.getNombre());
    }
}

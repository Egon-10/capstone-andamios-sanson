package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.model.Usuario;

import java.time.LocalDateTime;

/**
 * HU-33: registro de la bitácora tal como lo ve quien la consulta.
 *
 * Antes se devolvía la entidad, que arrastraba el usuario completo con su
 * documento, su teléfono y su correo. La bitácora solo necesita saber quién
 * fue: nombre y nombre de usuario (SEG-AP-05, minimización de datos).
 */
public record AuditoriaResponse(
        Long id,
        LocalDateTime fecha,
        String accion,
        String detalle,
        Long usuarioId,
        String usuario,
        String nombreUsuario) {

    public static AuditoriaResponse de(Auditoria a) {
        Usuario u = a.getUsuario();
        return new AuditoriaResponse(
                a.getId(),
                a.getFecha(),
                a.getAccion(),
                a.getDetalle(),
                u == null ? null : u.getId(),
                u == null ? "Sistema" : u.getNombreCompleto(),
                u == null ? null : u.getNombreUsuario());
    }
}

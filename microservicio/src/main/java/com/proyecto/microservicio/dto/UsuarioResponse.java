package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.model.Usuario;

import java.time.LocalDateTime;

/** Datos de usuario que expone la API. Nunca incluye la contraseña. */
public record UsuarioResponse(
        Long id,
        String nombre,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        String correo,
        String telefono,
        String nombreUsuario,
        String area,
        String turno,
        String estado,
        LocalDateTime fechaCreacion,
        RolResponse rol,
        boolean bloqueado,
        LocalDateTime bloqueadoHasta,
        boolean debeCambiarPassword) {

    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNombre(),
                u.getApellidos(),
                u.getTipoDocumento(),
                u.getNumeroDocumento(),
                u.getCorreo(),
                u.getTelefono(),
                u.getNombreUsuario(),
                u.getArea(),
                u.getTurno(),
                u.estaActivo() ? Usuario.ESTADO_ACTIVO : Usuario.ESTADO_INACTIVO,
                u.getFechaCreacion(),
                RolResponse.from(u.getRol()),
                u.estaBloqueado(ZonaHoraria.ahora()),
                u.estaBloqueado(ZonaHoraria.ahora()) ? u.getBloqueadoHasta() : null,
                u.isDebeCambiarPassword());
    }
}

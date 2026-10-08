package com.proyecto.microservicio.dto;

import com.proyecto.microservicio.model.Acceso;
import com.proyecto.microservicio.model.Usuario;

import java.time.LocalDateTime;

/** HU-32: intento de inicio de sesión tal como se muestra en la bitácora. */
public record AccesoResponse(
        Long id,
        LocalDateTime fecha,
        String identificador,
        Long usuarioId,
        String usuario,
        String resultado,
        String motivo,
        String ip,
        String agente) {

    public static AccesoResponse de(Acceso a) {
        Usuario u = a.getUsuario();
        return new AccesoResponse(
                a.getId(),
                a.getFecha(),
                a.getIdentificador(),
                u == null ? null : u.getId(),
                u == null ? null : u.getNombreCompleto(),
                a.getResultado(),
                a.getMotivo(),
                a.getIp(),
                a.getAgente());
    }
}

package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * HU-07: edición parcial (PATCH). Solo se modifican los campos que llegan
 * con valor; los que llegan nulos se conservan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioActualizacionRequest(

        @Size(min = 2, max = 60, message = "Los nombres deben tener entre 2 y 60 caracteres")
        @Pattern(regexp = ValidacionUsuario.SOLO_LETRAS, message = "Los nombres solo admiten letras y espacios")
        String nombres,

        @Size(min = 2, max = 60, message = "Los apellidos deben tener entre 2 y 60 caracteres")
        @Pattern(regexp = ValidacionUsuario.SOLO_LETRAS, message = "Los apellidos solo admiten letras y espacios")
        String apellidos,

        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 100, message = "El correo admite hasta 100 caracteres")
        String correo,

        @Pattern(regexp = ValidacionUsuario.TELEFONO, message = "El teléfono solo admite dígitos (máximo 15)")
        String telefono,

        @Pattern(regexp = ValidacionUsuario.AREAS, message = "Área no válida")
        String area,

        @Pattern(regexp = ValidacionUsuario.TURNOS, message = "Turno no válido")
        String turno,

        Long rolId,

        @Pattern(regexp = "ACTIVO|INACTIVO", message = "Estado no válido")
        String estado,

        @Pattern(regexp = ValidacionUsuario.PASSWORD, message = ValidacionUsuario.MENSAJE_PASSWORD)
        String password,

        String confirmarPassword) {
}

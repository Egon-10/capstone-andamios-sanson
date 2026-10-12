package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-43: formulario de registro de usuario (12 campos más el turno, CAM-02). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioRegistroRequest(

        @NotBlank(message = "Ingrese los nombres")
        @Size(min = 2, max = 60, message = "Los nombres deben tener entre 2 y 60 caracteres")
        @Pattern(regexp = ValidacionUsuario.SOLO_LETRAS, message = "Los nombres solo admiten letras y espacios")
        String nombres,

        @NotBlank(message = "Ingrese los apellidos")
        @Size(min = 2, max = 60, message = "Los apellidos deben tener entre 2 y 60 caracteres")
        @Pattern(regexp = ValidacionUsuario.SOLO_LETRAS, message = "Los apellidos solo admiten letras y espacios")
        String apellidos,

        @NotBlank(message = "Seleccione el tipo de documento")
        @Pattern(regexp = ValidacionUsuario.TIPOS_DOCUMENTO, message = "Tipo de documento no válido")
        String tipoDocumento,

        @NotBlank(message = "Ingrese el número de documento")
        @Size(max = 20, message = "El número de documento admite hasta 20 caracteres")
        String numeroDocumento,

        @NotBlank(message = "Ingrese el correo electrónico")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 100, message = "El correo admite hasta 100 caracteres")
        String correo,

        @Pattern(regexp = ValidacionUsuario.TELEFONO, message = "El teléfono solo admite dígitos (máximo 15)")
        String telefono,

        @NotBlank(message = "Ingrese el nombre de usuario")
        @Pattern(regexp = ValidacionUsuario.NOMBRE_USUARIO, message = "El nombre de usuario debe tener entre 4 y 30 caracteres, sin espacios")
        String nombreUsuario,

        @NotBlank(message = "Ingrese la contraseña")
        @Pattern(regexp = ValidacionUsuario.PATRON_CLAVE, message = ValidacionUsuario.MENSAJE_CLAVE)
        String password,

        @NotBlank(message = "Confirme la contraseña")
        String confirmarPassword,

        @NotNull(message = "Seleccione un rol")
        Long rolId,

        @NotBlank(message = "Seleccione el área")
        @Pattern(regexp = ValidacionUsuario.AREAS, message = "Área no válida")
        String area,

        @Pattern(regexp = ValidacionUsuario.TURNOS, message = "Turno no válido")
        String turno) {
}

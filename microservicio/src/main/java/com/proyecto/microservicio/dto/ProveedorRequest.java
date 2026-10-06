package com.proyecto.microservicio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-11: alta o edicion de un proveedor del catalogo maestro. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProveedorRequest(

        @NotBlank(message = "Ingrese la razón social del proveedor")
        @Size(max = 100, message = "La razón social admite hasta 100 caracteres")
        String nombre,

        @Pattern(regexp = "^$|^(10|15|16|17|20)\\d{9}$",
                message = "El RUC debe tener 11 dígitos y empezar con 10, 15, 16, 17 o 20")
        String ruc,

        @Size(max = 150, message = "La dirección admite hasta 150 caracteres")
        String direccion,

        @Pattern(regexp = "^$|^[0-9+ ]{6,15}$",
                message = "El teléfono admite solo números, espacios y el signo +")
        String telefono,

        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 100, message = "El correo admite hasta 100 caracteres")
        String correo) {
}

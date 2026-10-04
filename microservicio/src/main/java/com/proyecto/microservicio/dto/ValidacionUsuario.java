package com.proyecto.microservicio.dto;

/** Reglas de validación compartidas por los formularios de usuario (HU-43). */
public final class ValidacionUsuario {

    public static final String SOLO_LETRAS = "^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$";
    public static final String TIPOS_DOCUMENTO = "DNI|CE|PASAPORTE";
    public static final String TELEFONO = "^\\d{0,15}$";
    public static final String NOMBRE_USUARIO = "^\\S{4,30}$";
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$";
    public static final String MENSAJE_PASSWORD =
            "La contraseña debe tener al menos 8 caracteres, con una mayúscula, una minúscula y un número";
    public static final String AREAS = "LOGISTICA|PRODUCCION|COMERCIAL|ADMINISTRACION";
    public static final String TURNOS = "MANANA|TARDE|NOCHE";

    private ValidacionUsuario() {
    }
}

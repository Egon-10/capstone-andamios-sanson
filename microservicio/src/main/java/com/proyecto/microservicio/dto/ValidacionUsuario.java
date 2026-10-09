package com.proyecto.microservicio.dto;

/** Reglas de validación compartidas por los formularios de usuario (HU-43). */
public final class ValidacionUsuario {

    public static final String SOLO_LETRAS = "^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$";
    public static final String TIPOS_DOCUMENTO = "DNI|CE|PASAPORTE";
    public static final String TELEFONO = "^\\d{0,15}$";
    public static final String NOMBRE_USUARIO = "^\\S{4,30}$";
    /** HU-37: primera barrera de la política; la regla completa está en PoliticaContrasena. */
    public static final String PATRON_CLAVE = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$";
    public static final String MENSAJE_CLAVE =
            "La contraseña debe tener entre 10 y 72 caracteres, con mayúscula, minúscula, número y símbolo";
    public static final String AREAS = "LOGISTICA|PRODUCCION|COMERCIAL|ADMINISTRACION";
    /** Vacío = sin turno asignado (CAM-02: el turno es opcional). */
    public static final String TURNOS = "MANANA|TARDE|NOCHE|";

    private ValidacionUsuario() {
    }
}

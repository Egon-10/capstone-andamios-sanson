package com.proyecto.microservicio.model;

import java.text.Normalizer;
import java.util.Locale;

/** Nombres de los roles del sistema, tal como están registrados en la tabla roles. */
public final class Roles {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String GERENTE = "GERENTE";
    public static final String ENCARGADO = "ENCARGADO";

    private Roles() {
    }

    /** Convierte el nombre del rol a la autoridad usada en el token (sin tildes ni espacios). */
    public static String normalizar(String nombreRol) {
        if (nombreRol == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(nombreRol.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
    }
}

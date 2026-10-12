package com.proyecto.microservicio.reporte;

import com.proyecto.microservicio.exception.ReglaNegocioException;

import java.util.Locale;

/** HU-26 y HU-27: formatos de exportación disponibles. */
public enum Formato {

    PDF("pdf", "application/pdf"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String tipoMime;

    Formato(String extension, String tipoMime) {
        this.extension = extension;
        this.tipoMime = tipoMime;
    }

    public String extension() {
        return extension;
    }

    public String tipoMime() {
        return tipoMime;
    }

    /** Acepta "pdf", "xlsx" o "excel", sin distinguir mayúsculas. */
    public static Formato desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return PDF;
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "pdf" -> PDF;
            case "xlsx", "excel" -> EXCEL;
            default -> throw new ReglaNegocioException("formato",
                    "Formato no soportado: " + valor + ". Use pdf o xlsx");
        };
    }
}

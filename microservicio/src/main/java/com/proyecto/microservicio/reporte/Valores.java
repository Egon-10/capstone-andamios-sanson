package com.proyecto.microservicio.reporte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formato de los valores de un reporte para leerlos impresos (HU-26).
 *
 * Los importes usan coma de miles y punto decimal, como los comprobantes en
 * el Perú, y se anteponen con "S/". Las fechas van en día/mes/año.
 */
public final class Valores {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormatSymbols SIMBOLOS = DecimalFormatSymbols.getInstance(Locale.US);

    private Valores() {
    }

    public static String fecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(FECHA);
    }

    public static String fechaHora(LocalDateTime fecha) {
        return fecha == null ? "" : fecha.format(FECHA_HORA);
    }

    public static String moneda(BigDecimal valor) {
        return valor == null ? "" : "S/ " + decimal(valor);
    }

    public static String decimal(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        return new DecimalFormat("#,##0.00", SIMBOLOS).format(valor.setScale(2, RoundingMode.HALF_UP));
    }

    public static String entero(Number valor) {
        return valor == null ? "" : new DecimalFormat("#,##0", SIMBOLOS).format(valor.longValue());
    }

    /** Texto de una celda según el tipo de su columna. */
    public static String texto(Object valor, Reporte.Tipo tipo) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof LocalDateTime f) {
            return fechaHora(f);
        }
        if (valor instanceof LocalDate f) {
            return fecha(f);
        }
        if (valor instanceof Number n) {
            BigDecimal b = n instanceof BigDecimal d ? d : new BigDecimal(n.toString());
            return switch (tipo) {
                case MONEDA -> moneda(b);
                case DECIMAL -> decimal(b);
                case ENTERO -> entero(n);
                default -> b.toPlainString();
            };
        }
        return String.valueOf(valor);
    }
}

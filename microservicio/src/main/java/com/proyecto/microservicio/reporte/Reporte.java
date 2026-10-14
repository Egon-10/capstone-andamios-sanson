package com.proyecto.microservicio.reporte;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HU-26 y HU-27: contenido de un reporte, independiente del formato.
 *
 * Cada reporte se arma una sola vez como tabla y después se dibuja en PDF o
 * en Excel. Así los dos formatos muestran siempre los mismos datos, con los
 * mismos filtros y los mismos totales, y agregar un formato nuevo no obliga a
 * tocar ninguna consulta.
 *
 * @param titulo     nombre del reporte
 * @param filtros    filtros aplicados, ya redactados para el lector
 * @param columnas   encabezados y tipo de dato de cada columna
 * @param filas      valores de cada fila, en el orden de las columnas
 * @param totales    fila de totales, o nula si el reporte no la tiene
 * @param notas      aclaraciones que deben acompañar a las cifras
 * @param emitidoPor nombre de quien generó el reporte
 * @param emision    fecha y hora de generación en la zona del negocio
 */
public record Reporte(
        String titulo,
        List<String> filtros,
        List<Columna> columnas,
        List<List<Object>> filas,
        List<Object> totales,
        List<String> notas,
        String emitidoPor,
        LocalDateTime emision) {

    public Reporte {
        filtros = filtros == null ? List.of() : List.copyOf(filtros);
        columnas = List.copyOf(columnas);
        filas = filas == null ? List.of() : filas;
        notas = notas == null ? List.of() : List.copyOf(notas);
        for (List<Object> fila : filas) {
            if (fila.size() != columnas.size()) {
                throw new IllegalArgumentException("Cada fila debe tener " + columnas.size() + " valores");
            }
        }
        if (totales != null && totales.size() != columnas.size()) {
            throw new IllegalArgumentException("La fila de totales debe tener " + columnas.size() + " valores");
        }
    }

    /** Tipo de dato de una columna: decide la alineación y el formato numérico. */
    public enum Tipo {
        TEXTO, ENTERO, DECIMAL, MONEDA, FECHA, FECHA_HORA;

        public boolean esNumero() {
            return this == ENTERO || this == DECIMAL || this == MONEDA;
        }
    }

    /**
     * @param titulo encabezado de la columna
     * @param tipo   tipo de dato
     * @param ancho  ancho relativo en el PDF y aproximado en caracteres en Excel
     */
    public record Columna(String titulo, Tipo tipo, int ancho) {
    }

    public static Columna texto(String titulo, int ancho) {
        return new Columna(titulo, Tipo.TEXTO, ancho);
    }

    public static Columna entero(String titulo) {
        return new Columna(titulo, Tipo.ENTERO, 9);
    }

    public static Columna moneda(String titulo) {
        return new Columna(titulo, Tipo.MONEDA, 13);
    }

    public static Columna decimal(String titulo) {
        return new Columna(titulo, Tipo.DECIMAL, 11);
    }

    public static Columna fechaHora(String titulo) {
        return new Columna(titulo, Tipo.FECHA_HORA, 15);
    }

    public static Columna fecha(String titulo) {
        return new Columna(titulo, Tipo.FECHA, 11);
    }
}

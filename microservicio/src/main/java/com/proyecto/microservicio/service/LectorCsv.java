package com.proyecto.microservicio.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lector de archivos CSV para la carga masiva (HU-19).
 *
 * Se escribió a mano en lugar de agregar una dependencia porque el formato que
 * hace falta es acotado y conocido, y porque las dos cosas que suelen romper
 * una carga desde Excel son fáciles de cubrir aquí:
 *
 * - El separador. Excel en configuración regional española exporta con punto y
 *   coma, no con coma. Se detecta a partir de la cabecera en lugar de obligar
 *   al usuario a reformatear el archivo.
 *
 * - La marca de orden de bytes. Excel la escribe al inicio del archivo, y sin
 *   quitarla la primera columna de la cabecera no coincide con su nombre y
 *   todas las filas se rechazan sin que se entienda por qué.
 *
 * Soporta campos entre comillas con el separador dentro, y comillas dobladas
 * para representar una comilla literal, que es lo que marca el RFC 4180.
 */
final class LectorCsv {

    private static final char COMILLA = '"';
    private static final String MARCA_ORDEN_BYTES = "\uFEFF";

    private LectorCsv() {
    }

    /** Separador más frecuente en la cabecera: coma, punto y coma o tabulador. */
    static char detectarSeparador(String cabecera) {
        int comas = contar(cabecera, ',');
        int puntoYComa = contar(cabecera, ';');
        int tabuladores = contar(cabecera, '\t');

        // Gana el que mas aparece. En caso de empate se prefiere la coma, luego
        // el punto y coma: es el orden en que son mas frecuentes.
        char elegido = ',';
        int maximo = comas;
        if (puntoYComa > maximo) {
            elegido = ';';
            maximo = puntoYComa;
        }
        if (tabuladores > maximo) {
            elegido = '\t';
        }
        return elegido;
    }

    /**
     * Divide el contenido en filas de campos. La primera fila es la cabecera.
     * Las líneas en blanco se descartan, porque un archivo de Excel casi
     * siempre termina con una.
     */
    static List<List<String>> leer(String contenido) {
        String texto = contenido == null ? "" : contenido;
        if (texto.startsWith(MARCA_ORDEN_BYTES)) {
            texto = texto.substring(1);
        }
        texto = texto.replace("\r\n", "\n").replace('\r', '\n');

        int primerSalto = texto.indexOf('\n');
        String cabecera = primerSalto < 0 ? texto : texto.substring(0, primerSalto);
        char separador = detectarSeparador(cabecera);

        List<List<String>> filas = new ArrayList<>();
        for (String linea : dividirEnLineas(texto)) {
            if (linea.isBlank()) {
                continue;
            }
            filas.add(dividirCampos(linea, separador));
        }
        return filas;
    }

    /**
     * Separa las líneas respetando los saltos que estén dentro de un campo
     * entre comillas, que de otro modo partirían la fila en dos.
     */
    private static List<String> dividirEnLineas(String texto) {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == COMILLA) {
                entreComillas = !entreComillas;
                actual.append(c);
            } else if (c == '\n' && !entreComillas) {
                lineas.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    private static List<String> dividirCampos(String linea, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        int i = 0;

        while (i < linea.length()) {
            char c = linea.charAt(i);

            if (entreComillas && esComillaDoblada(linea, i)) {
                // Dos comillas seguidas dentro de un campo son una comilla literal.
                actual.append(COMILLA);
                i += 2;
                continue;
            }

            if (c == COMILLA) {
                entreComillas = !entreComillas;
            } else if (c == separador && !entreComillas) {
                campos.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
            i++;
        }
        campos.add(actual.toString().trim());
        return campos;
    }

    private static boolean esComillaDoblada(String linea, int i) {
        return linea.charAt(i) == COMILLA
                && i + 1 < linea.length()
                && linea.charAt(i + 1) == COMILLA;
    }

    private static int contar(String texto, char buscado) {
        int total = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == buscado) {
                total++;
            }
        }
        return total;
    }
}

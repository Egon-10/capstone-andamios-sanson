package com.proyecto.microservicio.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-19: lectura del archivo de carga masiva.
 *
 * Las pruebas cubren lo que de verdad llega desde Excel, que es la causa
 * habitual de que una carga falle sin que se entienda por qué.
 */
class LectorCsvTest {

    @Test
    @DisplayName("Lee una cabecera y sus filas separadas por coma")
    void separadorComa() {
        List<List<String>> filas = LectorCsv.leer("sku,nombre,precio\nAND-1,Marco,300\n");

        assertEquals(2, filas.size());
        assertEquals(List.of("sku", "nombre", "precio"), filas.get(0));
        assertEquals(List.of("AND-1", "Marco", "300"), filas.get(1));
    }

    @Test
    @DisplayName("Detecta el punto y coma que usa Excel en configuracion regional espanola")
    void separadorPuntoYComa() {
        List<List<String>> filas = LectorCsv.leer("sku;nombre;precio\nAND-1;Marco;300");

        assertEquals(List.of("sku", "nombre", "precio"), filas.get(0));
        assertEquals(List.of("AND-1", "Marco", "300"), filas.get(1));
    }

    @Test
    @DisplayName("Detecta el tabulador")
    void separadorTabulador() {
        assertEquals('\t', LectorCsv.detectarSeparador("sku\tnombre\tprecio"));
        assertEquals(';', LectorCsv.detectarSeparador("sku;nombre;precio"));
        assertEquals(',', LectorCsv.detectarSeparador("sku,nombre,precio"));
    }

    @Test
    @DisplayName("Descarta la marca de orden de bytes que Excel escribe al inicio")
    void marcaDeOrdenDeBytes() {
        // Sin quitarla, la primera columna se llamaria "\uFEFFsku" y no
        // coincidiria con el nombre esperado: todas las filas se rechazarian.
        List<List<String>> filas = LectorCsv.leer("\uFEFFsku,nombre\nAND-1,Marco");

        assertEquals("sku", filas.get(0).get(0));
    }

    @Test
    @DisplayName("Respeta el separador que esta dentro de un campo entre comillas")
    void separadorDentroDeComillas() {
        List<List<String>> filas = LectorCsv.leer(
                "sku,nombre,descripcion\nAND-1,\"Marco 1,50 m\",\"Galvanizado, reforzado\"");

        assertEquals(3, filas.get(1).size());
        assertEquals("Marco 1,50 m", filas.get(1).get(1));
        assertEquals("Galvanizado, reforzado", filas.get(1).get(2));
    }

    @Test
    @DisplayName("Una comilla doblada representa una comilla literal")
    void comillaDoblada() {
        List<List<String>> filas = LectorCsv.leer(
                "sku,nombre\nRUE-1,\"Rueda de 8\"\" de nylon\"");

        assertEquals("Rueda de 8\" de nylon", filas.get(1).get(1));
    }

    @Test
    @DisplayName("Un salto de linea dentro de un campo no parte la fila")
    void saltoDentroDeCampo() {
        List<List<String>> filas = LectorCsv.leer(
                "sku,descripcion\nAND-1,\"Primera linea\nSegunda linea\"");

        assertEquals(2, filas.size());
        assertTrue(filas.get(1).get(1).contains("\n"));
    }

    @Test
    @DisplayName("Acepta los finales de linea de Windows y descarta la linea final vacia")
    void finalesDeLineaDeWindows() {
        List<List<String>> filas = LectorCsv.leer("sku,nombre\r\nAND-1,Marco\r\n");

        assertEquals(2, filas.size());
        assertEquals(List.of("AND-1", "Marco"), filas.get(1));
    }

    @Test
    @DisplayName("Un archivo vacio devuelve una lista vacia en lugar de fallar")
    void archivoVacio() {
        assertTrue(LectorCsv.leer("").isEmpty());
        assertTrue(LectorCsv.leer(null).isEmpty());
        assertTrue(LectorCsv.leer("\n\n  \n").isEmpty());
    }
}

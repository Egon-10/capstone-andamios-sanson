package com.proyecto.microservicio.security;

import com.proyecto.microservicio.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CP-38: política de complejidad de contraseñas (HU-37). */
class PoliticaContrasenaTest {

    @Test
    @DisplayName("CP-38: acepta una contraseña que cumple todas las reglas")
    void aceptaValida() {
        assertTrue(PoliticaContrasena.incumplimientos("Clave#Segura2026", "ana.perez", "45678912").isEmpty());
    }

    @ParameterizedTest(name = "CP-38: rechaza \"{0}\" porque {1}")
    @CsvSource({
            "Ab#1,                    al menos 10",
            "clave#segura2026,        mayúscula",
            "CLAVE#SEGURA2026,        minúscula",
            "Clave#SeguraXYZ,         número",
            "ClaveSegura2026,         símbolo",
            "Clave Segura#2026,       espacios",
            "Ana.Perez#2026x,         nombre de usuario",
            "Doc#45678912ab,          documento",
            "Andamios-2026!,          previsible"
    })
    void rechaza(String clave, String motivo) {
        List<String> faltas = PoliticaContrasena.incumplimientos(clave, "ana.perez", "45678912");
        assertTrue(faltas.stream().anyMatch(f -> f.toLowerCase().contains(motivo.trim().toLowerCase())),
                () -> "Se esperaba una falta por " + motivo + " y se obtuvo " + faltas);
    }

    @Test
    @DisplayName("CP-38: no admite más de 72 bytes, el límite de BCrypt")
    void limiteDeBcrypt() {
        String larga = "Aa1#" + "x".repeat(69);
        assertTrue(PoliticaContrasena.incumplimientos(larga, null, null).stream().anyMatch(f -> f.contains("72")));
    }

    @Test
    @DisplayName("CP-38: exigir lanza una regla de negocio con todas las faltas")
    void exigir() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> PoliticaContrasena.exigir("nueva", "corta", null, null));
        assertEquals("nueva", ex.getCampo());
        assertTrue(ex.getMessage().contains("10 caracteres"));
        assertThrows(ReglaNegocioException.class, () -> PoliticaContrasena.exigir("nueva", null, null, null));
    }

    @Test
    @DisplayName("CP-37: la contraseña temporal siempre cumple la política y no se repite")
    void temporal() {
        SecureRandom aleatorio = new SecureRandom();
        String anterior = "";
        for (int i = 0; i < 500; i++) {
            String t = PoliticaContrasena.generarTemporal(aleatorio);
            assertEquals(14, t.length());
            assertTrue(PoliticaContrasena.incumplimientos(t, null, null).isEmpty(), t);
            assertFalse(t.chars().anyMatch(c -> "0O1lI".indexOf(c) >= 0), "evita caracteres que se confunden: " + t);
            assertNotEquals(anterior, t);
            anterior = t;
        }
    }

    @Test
    @DisplayName("Un dato corto no cuenta como contenido en la contraseña")
    void datoCorto() {
        assertTrue(PoliticaContrasena.incumplimientos("Clave#Ana2026", "ana", null).isEmpty());
        assertEquals("anaperez2026", PoliticaContrasena.normalizar("Ana.Perez-2026"));
    }
}

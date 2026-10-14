package com.proyecto.microservicio.security;

import com.proyecto.microservicio.exception.ReglaNegocioException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HU-37: política de complejidad de contraseñas (SEG-AP-03).
 *
 * Una contraseña válida tiene entre 10 y 72 caracteres, con mayúscula,
 * minúscula, número y símbolo; no contiene el nombre de usuario ni el número
 * de documento y no es una de las contraseñas más previsibles para esta
 * empresa. El tope de 72 no es arbitrario: BCrypt ignora lo que sigue al
 * byte 72, de modo que una contraseña más larga daría una falsa sensación de
 * seguridad.
 *
 * Es una clase sin dependencias de Spring para que la misma regla se pueda
 * probar de forma aislada y aplicar en el registro, el cambio y el
 * restablecimiento.
 */
public final class PoliticaContrasena {

    public static final int LARGO_MINIMO = 10;
    public static final int LARGO_MAXIMO_BYTES = 72;
    public static final String DESCRIPCION =
            "Entre 10 y 72 caracteres, con al menos una mayúscula, una minúscula, un número y un símbolo";

    /** Contraseñas previsibles en el contexto de la empresa, comparadas sin mayúsculas ni símbolos. */
    private static final Set<String> PREVISIBLES = Set.of(
            "andamios2026", "andamios2025", "sanson2026", "inventario2026", "password123",
            "contrasena123", "administrador1", "qwerty12345", "1234567890");

    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnpqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String SIMBOLOS = "!#$%&*+-=?@_";
    private static final int LARGO_TEMPORAL = 14;

    private PoliticaContrasena() {
    }

    /**
     * Devuelve las reglas que la contraseña incumple, redactadas para el
     * usuario. Una lista vacía significa que es válida.
     */
    public static List<String> incumplimientos(String clave, String nombreUsuario, String documento) {
        List<String> faltas = new ArrayList<>();
        if (clave == null || clave.length() < LARGO_MINIMO) {
            faltas.add("Debe tener al menos " + LARGO_MINIMO + " caracteres");
            if (clave == null) {
                return faltas;
            }
        }
        if (clave.getBytes(StandardCharsets.UTF_8).length > LARGO_MAXIMO_BYTES) {
            faltas.add("No puede superar los " + LARGO_MAXIMO_BYTES + " caracteres");
        }
        if (clave.chars().noneMatch(Character::isUpperCase)) {
            faltas.add("Debe incluir una letra mayúscula");
        }
        if (clave.chars().noneMatch(Character::isLowerCase)) {
            faltas.add("Debe incluir una letra minúscula");
        }
        if (clave.chars().noneMatch(Character::isDigit)) {
            faltas.add("Debe incluir un número");
        }
        if (clave.chars().allMatch(Character::isLetterOrDigit)) {
            faltas.add("Debe incluir un símbolo, por ejemplo ! # $ % & * @");
        }
        if (clave.chars().anyMatch(Character::isWhitespace)) {
            faltas.add("No puede contener espacios");
        }
        String normalizada = normalizar(clave);
        if (contiene(normalizada, nombreUsuario)) {
            faltas.add("No puede contener su nombre de usuario");
        }
        if (contiene(normalizada, documento)) {
            faltas.add("No puede contener su número de documento");
        }
        if (PREVISIBLES.contains(normalizada)) {
            faltas.add("Es demasiado previsible; elija otra");
        }
        return faltas;
    }

    /** Lanza una regla de negocio con todas las faltas, asociada al campo indicado. */
    public static void exigir(String campo, String clave, String nombreUsuario, String documento) {
        List<String> faltas = incumplimientos(clave, nombreUsuario, documento);
        if (!faltas.isEmpty()) {
            throw new ReglaNegocioException(campo, "La contraseña no cumple la política: "
                    + String.join("; ", faltas).toLowerCase(Locale.ROOT) + ".");
        }
    }

    /**
     * HU-36: contraseña temporal aleatoria que cumple la política. Evita los
     * caracteres que se confunden al dictarla o copiarla (0/O, 1/l/I).
     */
    public static String generarTemporal(SecureRandom aleatorio) {
        String todos = MAYUSCULAS + MINUSCULAS + NUMEROS + SIMBOLOS;
        List<Character> letras = new ArrayList<>(LARGO_TEMPORAL);
        letras.add(MAYUSCULAS.charAt(aleatorio.nextInt(MAYUSCULAS.length())));
        letras.add(MINUSCULAS.charAt(aleatorio.nextInt(MINUSCULAS.length())));
        letras.add(NUMEROS.charAt(aleatorio.nextInt(NUMEROS.length())));
        letras.add(SIMBOLOS.charAt(aleatorio.nextInt(SIMBOLOS.length())));
        while (letras.size() < LARGO_TEMPORAL) {
            letras.add(todos.charAt(aleatorio.nextInt(todos.length())));
        }
        // Mezcla de Fisher-Yates, para que los cuatro obligatorios no queden al inicio.
        for (int i = letras.size() - 1; i > 0; i--) {
            int j = aleatorio.nextInt(i + 1);
            Character t = letras.get(i);
            letras.set(i, letras.get(j));
            letras.set(j, t);
        }
        StringBuilder s = new StringBuilder(LARGO_TEMPORAL);
        letras.forEach(s::append);
        return s.toString();
    }

    private static boolean contiene(String claveNormalizada, String dato) {
        if (dato == null || dato.isBlank()) {
            return false;
        }
        String d = normalizar(dato);
        return d.length() >= 4 && claveNormalizada.contains(d);
    }

    /** Minúsculas y solo letras y números: "Andamios-2026!" y "andamios2026" son la misma idea. */
    static String normalizar(String valor) {
        return valor.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]", "");
    }
}

package com.proyecto.microservicio.exception;

/**
 * HU-35: la cuenta está bloqueada temporalmente por intentos fallidos (HTTP 423).
 *
 * Se responde con un mensaje propio y no con el genérico de credenciales
 * inválidas porque el usuario legítimo necesita saber que debe esperar o
 * pedir el desbloqueo. Esto revela que la cuenta existe, pero solo después de
 * cinco intentos fallidos, y el bloqueo se comprueba antes que la contraseña:
 * durante el bloqueo, acertar la contraseña no da ninguna señal.
 */
public class CuentaBloqueadaException extends RuntimeException {

    private final long minutosRestantes;

    public CuentaBloqueadaException(long minutosRestantes) {
        super("La cuenta está bloqueada temporalmente por intentos fallidos. Intente de nuevo en "
                + minutosRestantes + (minutosRestantes == 1 ? " minuto" : " minutos")
                + " o pida al administrador que la desbloquee.");
        this.minutosRestantes = minutosRestantes;
    }

    public long getMinutosRestantes() {
        return minutosRestantes;
    }
}

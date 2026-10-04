import { Injectable, inject, signal } from '@angular/core';

import { AuthService } from '../services/auth.service';

const EVENTOS_DE_ACTIVIDAD = ['click', 'keydown', 'mousemove', 'scroll', 'touchstart'];
const AVISO_PREVIO_MS = 60_000;

/**
 * HU-09: cierra la sesión tras el periodo máximo de inactividad y avisa
 * un minuto antes. Mientras el usuario está activo, renueva la sesión en
 * segundo plano para que el servidor no la dé por vencida.
 */
@Injectable({
  providedIn: 'root'
})
export class SesionInactividadService {

  private readonly auth = inject(AuthService);

  /** Segundos que faltan para el cierre, o null si no hay aviso. */
  readonly segundosRestantes = signal<number | null>(null);

  private ultimaActividad = Date.now();
  private ultimaRenovacion = Date.now();
  private intervalo: ReturnType<typeof setInterval> | null = null;

  private readonly registrarActividad = () => {
    this.ultimaActividad = Date.now();
  };

  iniciar(): void {
    if (this.intervalo) {
      return;
    }
    this.ultimaActividad = Date.now();
    this.ultimaRenovacion = Date.now();
    EVENTOS_DE_ACTIVIDAD.forEach(evento =>
      window.addEventListener(evento, this.registrarActividad, { passive: true })
    );
    this.intervalo = setInterval(() => this.verificar(), 1000);
  }

  detener(): void {
    EVENTOS_DE_ACTIVIDAD.forEach(evento =>
      window.removeEventListener(evento, this.registrarActividad)
    );
    if (this.intervalo) {
      clearInterval(this.intervalo);
      this.intervalo = null;
    }
    this.segundosRestantes.set(null);
  }

  /** Respuesta al aviso: el usuario decide seguir conectado. */
  continuar(): void {
    this.ultimaActividad = Date.now();
    this.segundosRestantes.set(null);
    this.renovar();
  }

  private verificar(): void {
    const limite = this.auth.inactividadMaximaMs;
    const inactivo = Date.now() - this.ultimaActividad;

    if (inactivo >= limite) {
      this.detener();
      this.auth.cerrarSesion('inactividad');
      return;
    }

    if (inactivo >= limite - AVISO_PREVIO_MS) {
      this.segundosRestantes.set(Math.ceil((limite - inactivo) / 1000));
      return;
    }

    this.segundosRestantes.set(null);

    const usuarioActivo = inactivo < limite / 3;
    if (usuarioActivo && Date.now() - this.ultimaRenovacion > limite / 3) {
      this.renovar();
    }
  }

  private renovar(): void {
    this.ultimaRenovacion = Date.now();
    this.auth.renovar().subscribe({ error: () => undefined });
  }
}

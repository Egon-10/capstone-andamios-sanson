import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * HU-40: estados de carga, vacío y error, iguales en todas las vistas.
 *
 * Una lista vacía no es lo mismo que una que no cargó: si se muestra "no hay
 * registros" cuando en realidad falló la consulta, el usuario toma una
 * decisión sobre información que no vio. Por eso el error ofrece reintentar y
 * el vacío explica qué significa.
 */
@Component({
  selector: 'app-estado-vista',
  standalone: true,
  template: `
    @switch (estado) {
      @case ('cargando') {
        <div class="estado" role="status" aria-live="polite">
          <span class="giro" aria-hidden="true"></span>
          <span>{{ textoCargando }}</span>
        </div>
      }
      @case ('error') {
        <div class="estado error" role="alert">
          <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
          <div>
            <strong>{{ textoError }}</strong>
            @if (detalle) { <p>{{ detalle }}</p> }
          </div>
          <button type="button" class="ui-boton secundario chico" (click)="reintentar.emit()">
            <i class="fa-solid fa-rotate-right" aria-hidden="true"></i> Reintentar
          </button>
        </div>
      }
      @case ('vacio') {
        <div class="estado vacio" role="status">
          <i class="fa-regular fa-folder-open" aria-hidden="true"></i>
          <div>
            <strong>{{ textoVacio }}</strong>
            @if (detalle) { <p>{{ detalle }}</p> }
          </div>
        </div>
      }
    }
  `,
  styles: [`
    .estado {
      display: flex; align-items: center; gap: var(--espacio-3);
      padding: var(--espacio-5); color: var(--texto-tenue);
      background: var(--superficie); border: 1px dashed var(--borde-fuerte);
      border-radius: var(--radio); font-size: var(--texto-base);
    }
    .estado p { margin-top: 2px; font-size: var(--texto-chico); }
    .estado > i { font-size: 1.4rem; }
    .estado.error { color: var(--peligro); background: var(--peligro-fondo); border: 1px solid #fecaca; }
    .estado.error button { margin-left: auto; }
    .giro {
      width: 18px; height: 18px; border-radius: 50%;
      border: 3px solid var(--borde-fuerte); border-top-color: var(--color-primario);
      animation: girar 0.8s linear infinite;
    }
    @keyframes girar { to { transform: rotate(360deg); } }
  `]
})
export class EstadoVistaComponent {
  /** 'cargando', 'error', 'vacio' o 'listo' (no muestra nada). */
  @Input() estado: 'cargando' | 'error' | 'vacio' | 'listo' = 'listo';
  @Input() textoCargando = 'Cargando…';
  @Input() textoError = 'No se pudo cargar la información.';
  @Input() textoVacio = 'No hay registros.';
  @Input() detalle = '';
  @Output() reintentar = new EventEmitter<void>();
}

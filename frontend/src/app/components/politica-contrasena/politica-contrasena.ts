import { Component, Input } from '@angular/core';

import { REGLAS } from '../../core/politica-contrasena';

/**
 * HU-37: lista de reglas de la contraseña que se marca mientras el usuario
 * escribe. Cada regla lleva icono y texto, no solo color, y la lista se
 * anuncia a los lectores de pantalla de forma discreta.
 */
@Component({
  selector: 'app-politica-contrasena',
  standalone: true,
  template: `
    <ul class="politica" aria-label="Requisitos de la contraseña">
      @for (r of reglas; track r.id) {
        <li [class.cumple]="r.cumple(clave, usuario, documento)">
          <i class="fa-solid" [class.fa-circle-check]="r.cumple(clave, usuario, documento)"
             [class.fa-circle]="!r.cumple(clave, usuario, documento)" aria-hidden="true"></i>
          <span>{{ r.texto }}</span>
          <span class="ui-visualmente-oculto">{{ r.cumple(clave, usuario, documento) ? '(cumple)' : '(falta)' }}</span>
        </li>
      }
    </ul>
  `,
  styles: [`
    .politica { list-style: none; display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 4px 16px; font-size: var(--texto-chico); color: var(--texto-tenue); }
    li { display: flex; align-items: center; gap: 6px; }
    li i { font-size: 0.7rem; color: var(--borde-fuerte); }
    li.cumple { color: var(--exito); }
    li.cumple i { font-size: 0.85rem; color: var(--exito); }
  `]
})
export class PoliticaContrasenaComponent {
  @Input() clave = '';
  @Input() usuario?: string | null;
  @Input() documento?: string | null;

  readonly reglas = REGLAS;
}

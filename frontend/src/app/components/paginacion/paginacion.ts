import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DecimalPipe } from '@angular/common';

import { Pagina } from '../../models/pagina';

/** Controles de paginación comunes a los listados paginados en el servidor. */
@Component({
  selector: 'app-paginacion',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    @if (pagina && pagina.totalElementos > 0) {
      <nav class="ui-paginacion" aria-label="Paginación">
        <span>
          {{ desde() | number }}–{{ hasta() | number }} de {{ pagina.totalElementos | number }} {{ unidad }}
        </span>
        <div class="controles">
          <label class="ui-campo tamano">
            <span class="ui-visualmente-oculto">Registros por página</span>
            <select [value]="pagina.tamano" (change)="cambiarTamano($any($event.target).value)">
              @for (t of tamanos; track t) { <option [value]="t">{{ t }} por página</option> }
            </select>
          </label>
          <button type="button" class="ui-boton secundario chico" [disabled]="pagina.primera"
                  (click)="cambiar.emit(pagina.pagina - 1)" aria-label="Página anterior">
            <i class="fa-solid fa-chevron-left" aria-hidden="true"></i>
          </button>
          <span aria-live="polite">Página {{ pagina.pagina + 1 }} de {{ pagina.totalPaginas }}</span>
          <button type="button" class="ui-boton secundario chico" [disabled]="pagina.ultima"
                  (click)="cambiar.emit(pagina.pagina + 1)" aria-label="Página siguiente">
            <i class="fa-solid fa-chevron-right" aria-hidden="true"></i>
          </button>
        </div>
      </nav>
    }
  `,
  styles: [`.tamano select { min-height: 32px; padding: 4px 8px; font-size: var(--texto-chico); }`]
})
export class PaginacionComponent {
  @Input() pagina?: Pagina<unknown>;
  @Input() unidad = 'registros';
  @Output() cambiar = new EventEmitter<number>();
  @Output() cambiarTamanoPagina = new EventEmitter<number>();

  readonly tamanos = [10, 20, 50, 100];

  desde(): number {
    return this.pagina ? this.pagina.pagina * this.pagina.tamano + 1 : 0;
  }

  hasta(): number {
    return this.pagina ? this.pagina.pagina * this.pagina.tamano + this.pagina.contenido.length : 0;
  }

  cambiarTamano(valor: string): void {
    this.cambiarTamanoPagina.emit(Number(valor));
  }
}

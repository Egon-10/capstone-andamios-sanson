import { Component, ElementRef, HostListener, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AlertaService } from '../../services/alerta.service';
import { StockCritico } from '../../models/indicadores';

const ICONOS: Record<StockCritico['nivel'], string> = {
  AGOTADO: 'fa-circle-xmark', CRITICO: 'fa-triangle-exclamation', BAJO: 'fa-circle-exclamation'
};
const ETIQUETAS: Record<StockCritico['nivel'], string> = { AGOTADO: 'Agotado', CRITICO: 'Crítico', BAJO: 'Bajo' };

/**
 * HU-29: campana de alertas de reposición en la barra superior.
 *
 * El contador muestra cuántos productos están en alerta; el punto rojo, si hay
 * alertas que el usuario todavía no revisó. Al abrir la lista las alertas se
 * marcan como vistas. Las nuevas se anuncian a los lectores de pantalla.
 */
@Component({
  selector: 'app-alertas',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './alertas.html',
  styleUrl: './alertas.css'
})
export class AlertasComponent {

  readonly alertas = inject(AlertaService);
  private readonly elemento = inject(ElementRef<HTMLElement>);

  abierto = false;

  alternar(): void {
    this.abierto = !this.abierto;
    if (this.abierto) {
      // Se marcan al cerrar la lista, para que mientras está abierta se vea
      // cuáles eran las nuevas.
      return;
    }
    this.alertas.marcarVistas();
  }

  cerrar(): void {
    if (this.abierto) {
      this.abierto = false;
      this.alertas.marcarVistas();
    }
  }

  @HostListener('document:click', ['$event'])
  alHacerClicFuera(evento: Event): void {
    if (!this.elemento.nativeElement.contains(evento.target as Node)) {
      this.cerrar();
    }
  }

  @HostListener('document:keydown.escape')
  alPresionarEscape(): void {
    this.cerrar();
  }

  icono(nivel: StockCritico['nivel']): string {
    return ICONOS[nivel];
  }

  etiqueta(nivel: StockCritico['nivel']): string {
    return ETIQUETAS[nivel];
  }

  textoBoton(): string {
    const total = this.alertas.cantidad();
    const nuevas = this.alertas.nuevas().length;
    if (total === 0) {
      return 'Alertas de reposición: ninguna';
    }
    return `Alertas de reposición: ${total}` + (nuevas > 0 ? `, ${nuevas} sin revisar` : '');
  }
}

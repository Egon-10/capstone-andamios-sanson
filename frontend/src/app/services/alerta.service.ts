import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription, interval, startWith, switchMap, catchError, of } from 'rxjs';

import { StockCritico } from '../models/indicadores';
import { IndicadoresService } from './indicadores.service';

/**
 * HU-29: alertas de reposición en la interfaz.
 *
 * Consulta el stock crítico al iniciar la sesión, cada minuto y cada vez que
 * otra pantalla avisa que el inventario cambió (por ejemplo, al registrar una
 * salida). Distingue las alertas nuevas de las ya vistas para que el aviso no
 * se repita en cada consulta: una alerta es nueva si el producto no estaba en
 * la lista la última vez que el usuario la revisó, o si empeoró de nivel.
 */
@Injectable({ providedIn: 'root' })
export class AlertaService {

  static readonly INTERVALO_MS = 60_000;
  private static readonly CLAVE_VISTAS = 'alertasVistas';
  private static readonly GRAVEDAD: Record<StockCritico['nivel'], number> = { BAJO: 1, CRITICO: 2, AGOTADO: 3 };

  private readonly indicadores = inject(IndicadoresService);
  private suscripcion?: Subscription;

  /** Productos en alerta, del más urgente al menos urgente. */
  readonly alertas = signal<StockCritico[]>([]);
  /** Último nivel que el usuario vio de cada producto. */
  private readonly vistas = signal<Record<number, StockCritico['nivel']>>(this.leerVistas());
  /** Mensaje para lectores de pantalla cuando aparece una alerta nueva. */
  readonly anuncio = signal('');

  readonly nuevas = computed(() => this.alertas().filter(a => this.esNueva(a)));
  readonly cantidad = computed(() => this.alertas().length);

  iniciar(): void {
    if (this.suscripcion) {
      return;
    }
    this.suscripcion = interval(AlertaService.INTERVALO_MS).pipe(
      startWith(0),
      switchMap(() => this.indicadores.stockCritico().pipe(catchError(() => of(null))))
    ).subscribe(lista => this.recibir(lista));
  }

  detener(): void {
    this.suscripcion?.unsubscribe();
    this.suscripcion = undefined;
  }

  /** Lo llaman las pantallas que modifican el stock, para no esperar al siguiente minuto. */
  actualizar(): void {
    this.indicadores.stockCritico().pipe(catchError(() => of(null))).subscribe(lista => this.recibir(lista));
  }

  /** El usuario abrió la lista: todo lo que ve deja de ser nuevo. */
  marcarVistas(): void {
    const vistas: Record<number, StockCritico['nivel']> = {};
    for (const a of this.alertas()) {
      vistas[a.productoId] = a.nivel;
    }
    this.vistas.set(vistas);
    this.anuncio.set('');
    this.guardarVistas(vistas);
  }

  esNueva(a: StockCritico): boolean {
    const vista = this.vistas()[a.productoId];
    return vista === undefined || AlertaService.GRAVEDAD[a.nivel] > AlertaService.GRAVEDAD[vista];
  }

  /**
   * Un error de red no borra las alertas que ya se mostraban: es preferible
   * una lista de hace un minuto que hacer creer que el stock se normalizó.
   */
  private recibir(lista: StockCritico[] | null): void {
    if (lista === null) {
      return;
    }
    const nuevasAntes = new Set(this.nuevas().map(a => a.productoId));
    this.alertas.set(lista);
    const recientes = this.nuevas().filter(a => !nuevasAntes.has(a.productoId));
    if (recientes.length === 1) {
      this.anuncio.set(`Alerta de reposición: ${recientes[0].producto} (${etiqueta(recientes[0].nivel)}).`);
    } else if (recientes.length > 1) {
      this.anuncio.set(`${recientes.length} productos nuevos en alerta de reposición.`);
    }
  }

  private leerVistas(): Record<number, StockCritico['nivel']> {
    try {
      return JSON.parse(sessionStorage.getItem(AlertaService.CLAVE_VISTAS) ?? '{}');
    } catch {
      return {};
    }
  }

  private guardarVistas(vistas: Record<number, StockCritico['nivel']>): void {
    try {
      sessionStorage.setItem(AlertaService.CLAVE_VISTAS, JSON.stringify(vistas));
    } catch {
      // Sin almacenamiento, las vistas valen solo para esta pestaña.
    }
  }
}

export function etiqueta(nivel: StockCritico['nivel']): string {
  return nivel === 'AGOTADO' ? 'agotado' : nivel === 'CRITICO' ? 'crítico' : 'bajo';
}

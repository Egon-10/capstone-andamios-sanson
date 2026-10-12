import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Valorizacion, ValorizacionProducto } from '../../models/valorizacion';
import { InventarioService } from '../../services/inventario.service';
import { FormatoReporte, ReporteService } from '../../services/reporte.service';
import { mensajeDeError, mensajeDeErrorEnBlob } from '../../core/errores';
import { hoyEnLima } from '../reportes/reportes';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';

/**
 * HU-17 y HU-20: valorizacion del inventario y productos por reponer.
 * HU-28: valorizacion con corte a una fecha pasada.
 *
 * El inventario se valoriza al costo promedio ponderado y no al precio de
 * venta. La diferencia no es menor: valorizar al precio sobreestimaria el
 * inventario en el margen comercial y daria una cifra que no sirve para el
 * balance, asi que la pantalla lo dice de forma explicita.
 *
 * El acceso esta restringido al administrador y al gerente en el servidor,
 * porque es informacion economica del negocio.
 */
@Component({
  selector: 'app-valorizacion',
  standalone: true,
  imports: [CommonModule, FormsModule, EstadoVistaComponent],
  templateUrl: './valorizacion.html',
  styleUrl: './valorizacion.css'
})
export class ValorizacionComponent implements OnInit {

  valorizacion?: Valorizacion;
  porReponer: ValorizacionProducto[] = [];

  /** Vista activa: el resumen por categoria o el detalle por producto. */
  vista: 'resumen' | 'detalle' | 'reposicion' = 'resumen';

  soloReposicionEnDetalle = false;
  cargando = false;
  mensajeError = '';

  /** HU-28: dia de corte elegido (AAAA-MM-DD); vacio es la valorizacion de hoy. */
  fechaCorte = '';
  readonly hoy = hoyEnLima();

  descargando: FormatoReporte | null = null;
  mensajeDescarga = '';

  private readonly reportes = inject(ReporteService);

  constructor(private readonly inventarioService: InventarioService) {}

  ngOnInit(): void {
    this.cargar();

    this.inventarioService.porReponer().subscribe({
      next: p => (this.porReponer = p),
      error: () => (this.porReponer = [])
    });
  }

  /** HU-28: carga la valorizacion de hoy o la del dia de corte elegido. */
  cargar(): void {
    if (this.fechaCorte && this.fechaCorte > this.hoy) {
      this.mensajeError = 'La fecha de corte no puede ser posterior a hoy.';
      return;
    }
    this.cargando = true;
    this.mensajeError = '';
    this.inventarioService.valorizacion(this.fechaCorte || null).subscribe({
      next: v => {
        this.cargando = false;
        this.valorizacion = v;
      },
      error: (e: HttpErrorResponse) => {
        this.cargando = false;
        this.mensajeError = mensajeDeError(e, 'No se pudo cargar la valorizacion del inventario');
      }
    });
  }

  verHoy(): void {
    this.fechaCorte = '';
    this.cargar();
  }

  /** HU-26 y HU-27: el mismo calculo, exportado por el servidor. */
  exportar(formato: FormatoReporte): void {
    this.descargando = formato;
    this.mensajeDescarga = '';
    this.reportes.descargar('valorizacion', formato, { fecha: this.valorizacion?.fechaCorte ?? '' }).subscribe({
      next: nombre => {
        this.descargando = null;
        this.mensajeDescarga = `Se descargó ${nombre}.`;
      },
      error: (e: HttpErrorResponse) => {
        this.descargando = null;
        // El cuerpo del error llega como Blob y se lee de forma asincrona.
        void mensajeDeErrorEnBlob(e, 'No se pudo generar el reporte').then(m => (this.mensajeError = m));
      }
    });
  }

  /** HU-40: la primera carga muestra su estado; una recarga deja visibles los datos anteriores. */
  get estadoVista(): 'cargando' | 'error' | 'listo' {
    if (this.valorizacion) {
      return 'listo';
    }
    if (this.cargando) {
      return 'cargando';
    }
    return this.mensajeError ? 'error' : 'listo';
  }

  /** Detalle, filtrado a lo que necesita reposicion cuando se pide. */
  get detalle(): ValorizacionProducto[] {
    const todos = this.valorizacion?.detalle ?? [];
    return this.soloReposicionEnDetalle
      ? todos.filter(p => p.necesitaReposicion)
      : todos;
  }

  /** Valor acumulado de lo que hay que reponer, para dimensionar la compra. */
  get valorPorReponer(): number {
    return this.porReponer.reduce((suma, p) => suma + (p.valor ?? 0), 0);
  }
}

import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Producto } from '../../models/producto';
import { Ajuste } from '../../models/ajuste';
import { ProductoService } from '../../services/producto.service';
import { AjusteService } from '../../services/ajuste.service';
import { AlertaService } from '../../services/alerta.service';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';

type EstadoVista = EstadoVistaComponent['estado'];

/**
 * HU-16: ajustes de inventario por conteo fisico.
 *
 * La pantalla refleja el control que da sentido a la historia: quien cuenta y
 * quien autoriza son personas distintas. El encargado solo ve el formulario de
 * conteo y el historial; los botones de aprobar y rechazar aparecen unicamente
 * para el administrador y el gerente, y el servidor los restringe de todos
 * modos, asi que ocultarlos es comodidad y no seguridad.
 *
 * El formulario muestra la diferencia en vivo mientras se escribe el conteo,
 * porque es el dato que la persona necesita confirmar antes de enviar: un cero
 * de mas en el conteo se nota al ver la diferencia, no al ver el numero.
 */
@Component({
  selector: 'app-ajustes',
  standalone: true,
  imports: [CommonModule, FormsModule, EstadoVistaComponent],
  templateUrl: './ajustes.html',
  styleUrl: './ajustes.css'
})
export class AjustesComponent implements OnInit {

  productos: Producto[] = [];
  ajustes: Ajuste[] = [];

  productoSeleccionado?: Producto;
  stockFisico?: number;
  observacion = '';

  /** Ajuste sobre el que se abrio el cuadro de rechazo. */
  ajusteRechazando?: Ajuste;
  motivoRechazo = '';

  filtroEstado = '';
  rol = '';
  mensajeError = '';
  mensajeExito = '';
  errorCampo: { [campo: string]: string } = {};
  guardando = false;

  /** HU-40: estado de la consulta de conteos, distinto de los errores del formulario. */
  cargando = false;
  errorCarga = '';

  /** HU-29: aprobar un ajuste mueve el stock: se actualizan las alertas. */
  private readonly alertas = inject(AlertaService);

  constructor(
    private readonly productoService: ProductoService,
    private readonly ajusteService: AjusteService
  ) {}

  ngOnInit(): void {
    this.cargarRol();
    this.productoService.listar().subscribe(data => (this.productos = data));
    this.listar();
  }

  private cargarRol(): void {
    const guardado = localStorage.getItem('usuario');
    if (!guardado) {
      return;
    }
    try {
      this.rol = JSON.parse(guardado)?.rol?.nombre ?? '';
    } catch {
      this.rol = '';
    }
  }

  listar(): void {
    this.cargando = true;
    this.errorCarga = '';
    this.ajusteService.listar(this.filtroEstado || undefined).subscribe({
      next: data => {
        this.cargando = false;
        this.ajustes = data;
      },
      error: (e: HttpErrorResponse) => {
        this.cargando = false;
        this.mostrarError(e, 'No se pudieron cargar los ajustes');
        this.errorCarga = this.mensajeError;
      }
    });
  }

  /** HU-40: lo que muestra la lista de conteos (cargando, error, vacio o la tabla). */
  get estadoVista(): EstadoVista {
    if (this.cargando) {
      return 'cargando';
    }
    if (this.errorCarga) {
      return 'error';
    }
    return this.ajustes.length === 0 ? 'vacio' : 'listo';
  }

  /** Diferencia entre lo contado y lo que el sistema tiene registrado. */
  get diferencia(): number | undefined {
    if (!this.productoSeleccionado || this.stockFisico === undefined
        || this.stockFisico === null) {
      return undefined;
    }
    return this.stockFisico - (this.productoSeleccionado.stock ?? 0);
  }

  /** Texto de la diferencia con su signo, para que se lea sin ambiguedad. */
  get textoDiferencia(): string {
    const d = this.diferencia;
    if (d === undefined) {
      return '';
    }
    if (d === 0) {
      return 'El conteo coincide con el stock registrado: no hay nada que ajustar';
    }
    return d > 0
      ? `Sobran ${d} unidades respecto del sistema`
      : `Faltan ${Math.abs(d)} unidades respecto del sistema`;
  }

  registrar(): void {
    this.limpiarMensajes();

    if (!this.productoSeleccionado) {
      this.errorCampo['productoId'] = 'Seleccione el producto que conto';
      return;
    }
    if (this.stockFisico === undefined || this.stockFisico === null || this.stockFisico < 0) {
      this.errorCampo['stockFisico'] = 'Ingrese las unidades contadas';
      return;
    }
    if (this.diferencia === 0) {
      this.errorCampo['stockFisico'] =
        'El conteo coincide con el stock registrado: no hay nada que ajustar';
      return;
    }

    this.guardando = true;
    this.ajusteService
      .registrar(this.productoSeleccionado.id!, this.stockFisico,
        this.observacion.trim() || undefined)
      .subscribe({
        next: () => {
          this.guardando = false;
          this.mensajeExito =
            'Conteo registrado. Queda pendiente de aprobacion: el stock no cambio todavia.';
          this.productoSeleccionado = undefined;
          this.stockFisico = undefined;
          this.observacion = '';
          this.listar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando = false;
          this.mostrarError(e, 'No se pudo registrar el conteo');
        }
      });
  }

  aprobar(ajuste: Ajuste): void {
    this.limpiarMensajes();
    this.guardando = true;

    this.ajusteService.aprobar(ajuste.id!).subscribe({
      next: resultado => {
        this.guardando = false;
        this.mensajeExito = `Ajuste aprobado. Se registro el movimiento `
          + `${resultado.movimientoId} que corrige el inventario.`;
        this.listar();
        // El stock cambio, asi que la lista de productos del formulario
        // tambien: si no se recarga, el proximo conteo se calcularia contra
        // un stock vencido.
        this.productoService.listar().subscribe(data => (this.productos = data));
        this.alertas.actualizar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando = false;
        this.mostrarError(e, 'No se pudo aprobar el ajuste');
      }
    });
  }

  abrirRechazo(ajuste: Ajuste): void {
    this.limpiarMensajes();
    this.ajusteRechazando = ajuste;
    this.motivoRechazo = '';
  }

  cerrarRechazo(): void {
    this.ajusteRechazando = undefined;
    this.motivoRechazo = '';
    this.errorCampo = {};
  }

  confirmarRechazo(): void {
    if (!this.ajusteRechazando) {
      return;
    }
    if (this.motivoRechazo.trim().length < 10) {
      this.errorCampo['motivo'] =
        'Explique por que se rechaza, con al menos 10 caracteres';
      return;
    }

    this.guardando = true;
    this.ajusteService
      .rechazar(this.ajusteRechazando.id!, this.motivoRechazo.trim())
      .subscribe({
        next: () => {
          this.guardando = false;
          this.mensajeExito = 'Ajuste rechazado. El inventario no se modifico.';
          this.cerrarRechazo();
          this.listar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando = false;
          this.mostrarError(e, 'No se pudo rechazar el ajuste');
        }
      });
  }

  /** Solo el administrador y el gerente resuelven un ajuste (HU-16). */
  puedeResolver(): boolean {
    return this.rol === 'ADMINISTRADOR' || this.rol === 'GERENTE';
  }

  limpiarMensajes(): void {
    this.mensajeError = '';
    this.mensajeExito = '';
    this.errorCampo = {};
  }

  private mostrarError(e: HttpErrorResponse, porOmision: string): void {
    const cuerpo = e.error;
    this.mensajeError = cuerpo?.mensaje || porOmision;
    if (cuerpo?.errores) {
      this.errorCampo = cuerpo.errores;
    }
  }
}

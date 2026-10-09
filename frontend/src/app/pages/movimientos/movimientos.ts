import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Producto } from '../../models/producto';
import { MovimientoDetalle } from '../../models/movimiento-detalle';
import { Motivo } from '../../models/motivo';

import { ProductoService } from '../../services/producto.service';
import { MovimientoService, MovimientoSolicitud } from '../../services/movimiento.service';
import { MotivoService } from '../../services/motivo.service';
import { AlertaService } from '../../services/alerta.service';

/**
 * Pantalla de movimientos de inventario.
 *
 * Cambios del Sprint 2 respecto de la version anterior:
 *
 * - No se envia el identificador del usuario. El servidor lo toma del token
 *   de la sesion (CR-04), de modo que el cliente ya no puede decir a nombre
 *   de quien se registra un movimiento.
 *
 * - Desaparecen la edicion y el borrado. Un movimiento es inmutable: para
 *   corregirlo se anula (HU-15), y la anulacion exige una justificacion
 *   escrita que queda en el kardex.
 *
 * - El motivo es obligatorio y se elige del catalogo (HU-14). Los motivos que
 *   exigen nota habilitan el campo de observacion como requerido.
 *
 * - Ya no se llama al endpoint de auditoria desde el navegador. El servidor
 *   registra la auditoria de cada movimiento, que es lo correcto: una
 *   auditoria que depende de que el cliente la informe se puede omitir o
 *   falsificar simplemente no haciendo esa segunda llamada.
 */
@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './movimientos.html',
  styleUrl: './movimientos.css'
})
export class MovimientosComponent implements OnInit {

  productos: Producto[] = [];
  movimientos: MovimientoDetalle[] = [];
  movimientosFiltrados: MovimientoDetalle[] = [];

  motivosEntrada: Motivo[] = [];
  motivosSalida: Motivo[] = [];

  /** ENTRADA o SALIDA: determina que motivos se ofrecen. */
  tipo: 'ENTRADA' | 'SALIDA' = 'ENTRADA';

  productoSeleccionado?: Producto;
  cantidad = 1;
  motivoSeleccionado = '';
  observacion = '';
  costoUnitario?: number;

  /** Movimiento sobre el que se abrio el cuadro de anulacion. */
  movimientoAnulando?: MovimientoDetalle;
  justificacion = '';

  rol = '';
  mensajeError = '';
  mensajeExito = '';
  errorCampo: { [campo: string]: string } = {};
  guardando = false;

  fechaInicio = '';
  fechaFin = '';
  tipoFiltro = '';

  /** HU-29: tras mover stock se actualizan las alertas sin esperar al siguiente minuto. */
  private readonly alertas = inject(AlertaService);

  constructor(
    private readonly productoService: ProductoService,
    private readonly movimientoService: MovimientoService,
    private readonly motivoService: MotivoService
  ) {}

  ngOnInit(): void {
    this.cargarRol();
    this.listarProductos();
    this.listarMovimientos();
    this.cargarMotivos();
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

  private cargarMotivos(): void {
    this.motivoService.listar('ENTRADA').subscribe({
      next: m => (this.motivosEntrada = m),
      error: () => (this.motivosEntrada = [])
    });
    this.motivoService.listar('SALIDA').subscribe({
      next: m => (this.motivosSalida = m),
      error: () => (this.motivosSalida = [])
    });
  }

  listarProductos(): void {
    this.productoService.listar().subscribe(data => (this.productos = data));
  }

  listarMovimientos(): void {
    this.movimientoService.listar().subscribe(data => {
      this.movimientos = data;
      this.movimientosFiltrados = data;
    });
  }

  /** Motivos aplicables al tipo de movimiento elegido. */
  get motivos(): Motivo[] {
    return this.tipo === 'ENTRADA' ? this.motivosEntrada : this.motivosSalida;
  }

  /** HU-14: el motivo elegido puede exigir una observacion que lo explique. */
  get exigeNota(): boolean {
    return this.motivos.some(m => m.codigo === this.motivoSeleccionado && m.exigeNota);
  }

  /** El costo unitario solo interviene en las entradas (HU-17). */
  get pideCosto(): boolean {
    return this.tipo === 'ENTRADA';
  }

  cambiarTipo(tipo: 'ENTRADA' | 'SALIDA'): void {
    this.tipo = tipo;
    this.motivoSeleccionado = '';
    this.limpiarMensajes();
  }

  limpiarMensajes(): void {
    this.mensajeError = '';
    this.mensajeExito = '';
    this.errorCampo = {};
  }

  /**
   * Registra el movimiento. Las comprobaciones de aqui son solo para no
   * molestar al servidor con una solicitud que ya se sabe incompleta: la
   * validacion que decide es la del servidor, y su respuesta es la que se
   * muestra al usuario.
   */
  registrar(): void {
    this.limpiarMensajes();

    if (!this.productoSeleccionado) {
      this.errorCampo['productoId'] = 'Seleccione un producto';
      return;
    }
    if (!this.cantidad || this.cantidad <= 0) {
      this.errorCampo['cantidad'] = 'La cantidad debe ser mayor que cero';
      return;
    }
    if (!this.motivoSeleccionado) {
      this.errorCampo['motivo'] = 'Seleccione el motivo del movimiento';
      return;
    }
    if (this.exigeNota && !this.observacion.trim()) {
      this.errorCampo['observacion'] = 'Este motivo exige una observacion que lo explique';
      return;
    }

    const solicitud: MovimientoSolicitud = {
      productoId: this.productoSeleccionado.id!,
      cantidad: this.cantidad,
      motivo: this.motivoSeleccionado,
      observacion: this.observacion.trim() || undefined,
      costoUnitario: this.pideCosto ? this.costoUnitario : undefined
    };

    this.guardando = true;
    const peticion = this.tipo === 'ENTRADA'
      ? this.movimientoService.registrarEntrada(solicitud)
      : this.movimientoService.registrarSalida(solicitud);

    peticion.subscribe({
      next: () => {
        this.guardando = false;
        this.mensajeExito = this.tipo === 'ENTRADA'
          ? 'Entrada registrada correctamente'
          : 'Salida registrada correctamente';
        this.limpiarFormulario();
        this.listarProductos();
        this.listarMovimientos();
        this.alertas.actualizar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando = false;
        this.mostrarError(e, 'No se pudo registrar el movimiento');
      }
    });
  }

  private limpiarFormulario(): void {
    this.cantidad = 1;
    this.productoSeleccionado = undefined;
    this.motivoSeleccionado = '';
    this.observacion = '';
    this.costoUnitario = undefined;
  }

  // --- Anulacion (HU-15) ---

  abrirAnulacion(movimiento: MovimientoDetalle): void {
    this.limpiarMensajes();
    this.movimientoAnulando = movimiento;
    this.justificacion = '';
  }

  cerrarAnulacion(): void {
    this.movimientoAnulando = undefined;
    this.justificacion = '';
    this.errorCampo = {};
  }

  confirmarAnulacion(): void {
    if (!this.movimientoAnulando) {
      return;
    }
    if (this.justificacion.trim().length < 10) {
      this.errorCampo['justificacion'] =
        'Explique por que se anula, con al menos 10 caracteres';
      return;
    }

    this.guardando = true;
    this.movimientoService
      .anular(this.movimientoAnulando.id!, this.justificacion.trim())
      .subscribe({
        next: () => {
          this.guardando = false;
          this.mensajeExito =
            'Movimiento anulado. Se registro el asiento que lo compensa.';
          this.cerrarAnulacion();
          this.listarProductos();
          this.listarMovimientos();
          this.alertas.actualizar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando = false;
          this.mostrarError(e, 'No se pudo anular el movimiento');
        }
      });
  }

  /** Solo se anula un asiento vigente: ni uno ya anulado ni un compensatorio. */
  sePuedeAnular(movimiento: MovimientoDetalle): boolean {
    return this.puedeAnular() && movimiento.estado === 'REGISTRADO';
  }

  puedeAnular(): boolean {
    return this.rol === 'ADMINISTRADOR' || this.rol === 'GERENTE';
  }

  /**
   * Muestra el mensaje que devolvio el servidor. El contrato de error del
   * Sprint 1 trae un mensaje general y, cuando corresponde, el campo que lo
   * provoco, para poder marcarlo en el formulario.
   */
  private mostrarError(e: HttpErrorResponse, porOmision: string): void {
    const cuerpo = e.error;
    this.mensajeError = cuerpo?.mensaje || porOmision;
    if (cuerpo?.errores) {
      this.errorCampo = cuerpo.errores;
    }
  }

  aplicarFiltros(): void {
    this.movimientoService
      .filtrar(this.fechaInicio, this.fechaFin, this.tipoFiltro)
      .subscribe({
        next: data => (this.movimientosFiltrados = data),
        error: (e: HttpErrorResponse) =>
          this.mostrarError(e, 'No se pudieron filtrar los movimientos')
      });
  }

  limpiarFiltros(): void {
    this.fechaInicio = '';
    this.fechaFin = '';
    this.tipoFiltro = '';
    this.movimientosFiltrados = this.movimientos;
  }

  /** Clase CSS de la fila segun el estado, para distinguir los anulados. */
  claseEstado(movimiento: MovimientoDetalle): string {
    if (movimiento.estado === 'ANULADO') {
      return 'fila-anulada';
    }
    if (movimiento.estado === 'COMPENSACION') {
      return 'fila-compensacion';
    }
    return '';
  }
}

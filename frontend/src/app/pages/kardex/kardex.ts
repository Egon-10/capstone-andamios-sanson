import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Producto } from '../../models/producto';
import { Kardex } from '../../models/kardex';
import { ProductoService } from '../../services/producto.service';
import { InventarioService } from '../../services/inventario.service';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';

type EstadoVista = EstadoVistaComponent['estado'];

/**
 * HU-18: kardex por producto con saldo acumulado.
 *
 * El saldo de cada linea lo calcula el servidor reconstruyendolo desde el
 * stock actual hacia atras, porque los movimientos heredados de la base
 * anterior no registraron el suyo. Por eso la pantalla muestra el saldo
 * inicial: deja claro con que existencia arranca el historial y permite
 * comprobar que la ultima linea coincide con el stock del producto.
 */
@Component({
  selector: 'app-kardex',
  standalone: true,
  imports: [CommonModule, FormsModule, EstadoVistaComponent],
  templateUrl: './kardex.html',
  styleUrl: './kardex.css'
})
export class KardexComponent implements OnInit {

  productos: Producto[] = [];
  productoId?: number;
  desde = '';
  hasta = '';

  kardex?: Kardex;
  cargando = false;
  mensajeError = '';
  /** La lista de productos alimenta el selector: sin ella no se puede consultar nada. */
  errorProductos = '';

  constructor(
    private readonly productoService: ProductoService,
    private readonly inventarioService: InventarioService
  ) {}

  ngOnInit(): void {
    this.cargarProductos();
  }

  cargarProductos(): void {
    this.errorProductos = '';
    this.productoService.listar().subscribe({
      next: data => (this.productos = data),
      error: (e: HttpErrorResponse) =>
        (this.errorProductos = e.error?.mensaje || 'No se pudo cargar la lista de productos.')
    });
  }

  /**
   * HU-40: lo que muestra la consulta. Sin producto elegido es un estado
   * vacio que invita a elegir uno, no un error.
   */
  get estadoVista(): EstadoVista {
    if (this.cargando) {
      return 'cargando';
    }
    if (this.mensajeError) {
      return 'error';
    }
    if (!this.kardex || this.kardex.lineas.length === 0) {
      return 'vacio';
    }
    return 'listo';
  }

  get hayFechas(): boolean {
    return !!(this.desde || this.hasta);
  }

  get textoVacio(): string {
    if (!this.kardex) {
      return 'Seleccione un producto para ver su kardex.';
    }
    return this.hayFechas
      ? 'Este producto no tiene movimientos en el periodo seleccionado.'
      : 'Este producto todavía no tiene movimientos.';
  }

  get detalleVacio(): string {
    if (!this.kardex) {
      return 'Puede acotar el historial con un rango de fechas.';
    }
    return this.hayFechas
      ? 'Amplíe el rango de fechas o consulte todo el historial.'
      : 'Cuando se registre una entrada o una salida aparecerá aquí.';
  }

  consultar(): void {
    this.mensajeError = '';

    if (!this.productoId) {
      this.kardex = undefined;
      return;
    }

    this.cargando = true;
    this.inventarioService
      .kardex(this.productoId, this.desde || undefined, this.hasta || undefined)
      .subscribe({
        next: k => {
          this.cargando = false;
          this.kardex = k;
        },
        error: (e: HttpErrorResponse) => {
          this.cargando = false;
          this.kardex = undefined;
          this.mensajeError = e.error?.mensaje || 'No se pudo cargar el kardex';
        }
      });
  }

  limpiarFechas(): void {
    this.desde = '';
    this.hasta = '';
    this.consultar();
  }

  /**
   * Comprueba que el saldo de la ultima linea coincida con el stock del
   * producto. Si no coincide, el kardex no cuadra con el inventario y hay que
   * mirarlo: mostrarlo es mas util que ocultarlo.
   */
  get kardexCuadra(): boolean {
    if (!this.kardex || this.kardex.lineas.length === 0) {
      return true;
    }
    // Con filtro de fechas la ultima linea no es la ultima del producto, asi
    // que la comprobacion solo aplica al historial completo.
    if (this.desde || this.hasta) {
      return true;
    }
    return this.kardex.lineas.at(-1)?.saldo === this.kardex.stockActual;
  }

  claseEstado(estado?: string): string {
    if (estado === 'ANULADO') {
      return 'fila-anulada';
    }
    if (estado === 'COMPENSACION') {
      return 'fila-compensacion';
    }
    return '';
  }
}

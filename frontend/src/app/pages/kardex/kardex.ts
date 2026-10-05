import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Producto } from '../../models/producto';
import { Kardex } from '../../models/kardex';
import { ProductoService } from '../../services/producto.service';
import { InventarioService } from '../../services/inventario.service';

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
  imports: [CommonModule, FormsModule],
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

  constructor(
    private productoService: ProductoService,
    private inventarioService: InventarioService
  ) {}

  ngOnInit(): void {
    this.productoService.listar().subscribe(data => (this.productos = data));
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
    const ultima = this.kardex.lineas[this.kardex.lineas.length - 1];
    return ultima.saldo === this.kardex.stockActual;
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

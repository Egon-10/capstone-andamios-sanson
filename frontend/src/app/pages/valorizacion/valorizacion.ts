import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Valorizacion, ValorizacionProducto } from '../../models/valorizacion';
import { InventarioService } from '../../services/inventario.service';

/**
 * HU-17 y HU-20: valorizacion del inventario y productos por reponer.
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
  imports: [CommonModule, FormsModule],
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

  constructor(private inventarioService: InventarioService) {}

  ngOnInit(): void {
    this.cargando = true;

    this.inventarioService.valorizacion().subscribe({
      next: v => {
        this.cargando = false;
        this.valorizacion = v;
      },
      error: (e: HttpErrorResponse) => {
        this.cargando = false;
        this.mensajeError = e.error?.mensaje
          || 'No se pudo cargar la valorizacion del inventario';
      }
    });

    this.inventarioService.porReponer().subscribe({
      next: p => (this.porReponer = p),
      error: () => (this.porReponer = [])
    });
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

  /**
   * Exporta el detalle a CSV desde el navegador. Se genera aqui y no en el
   * servidor porque son los mismos datos que la pantalla ya tiene: pedir otro
   * endpoint para lo mismo solo agregaria una ruta que mantener.
   */
  descargarDetalle(): void {
    const filas = this.valorizacion?.detalle ?? [];
    if (filas.length === 0) {
      return;
    }

    const cabecera = ['SKU', 'Producto', 'Categoria', 'Stock',
      'Costo promedio', 'Valorizado', 'Necesita reposicion'];

    const lineas = filas.map(p => [
      p.sku ?? '',
      p.producto ?? '',
      p.categoria ?? '',
      String(p.stock ?? 0),
      String(p.costoPromedio ?? 0),
      String(p.valor ?? 0),
      p.necesitaReposicion ? 'Si' : 'No'
    ]);

    const csv = [cabecera, ...lineas]
      .map(f => f.map(c => `"${c.replace(/"/g, '""')}"`).join(';'))
      .join('\n');

    // La marca de orden de bytes hace que Excel reconozca el UTF-8 y no
    // muestre las tildes partidas.
    const contenido = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(contenido);

    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = `valorizacion-inventario-${new Date().toISOString().slice(0, 10)}.csv`;
    enlace.click();

    URL.revokeObjectURL(url);
  }
}

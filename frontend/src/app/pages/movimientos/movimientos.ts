import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Producto } from '../../models/producto';
import { MovimientoDetalle } from '../../models/movimiento-detalle';

import { ProductoService } from '../../services/producto.service';
import { MovimientoService } from '../../services/movimiento.service';
import { AuditoriaService }
from '../../services/auditoria.service';
@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './movimientos.html',
  styleUrl: './movimientos.css'
})
export class MovimientosComponent implements OnInit {

  productos: Producto[] = [];

  movimientos: MovimientoDetalle[] = [];

  productoSeleccionado?: Producto;

  cantidad: number = 1;

  movimientoEditando: any = null;
  usuarioId: number = 0;
  mensajeError = '';
  rol = '';

mensajeExito = '';
fechaInicio: string = '';
fechaFin: string = '';
tipoFiltro: string = '';

movimientosFiltrados: MovimientoDetalle[] = [];
  constructor(
    private productoService: ProductoService,
    private movimientoService: MovimientoService,
  private auditoriaService: AuditoriaService
  ) {}

  ngOnInit(): void {

    this.cargarUsuario();

    this.listarProductos();

    this.listarMovimientos();
    const usuario =
  localStorage.getItem('usuario');

if(usuario){

  this.rol =
    JSON.parse(usuario).rol.nombre;
}
  }

  cargarUsuario(): void {

    const usuarioGuardado =
      localStorage.getItem('usuario');

    if (usuarioGuardado) {

      const usuario =
        JSON.parse(usuarioGuardado);

      this.usuarioId = usuario.id;
    }
  }

  listarProductos(): void {

    this.productoService
      .listar()
      .subscribe(data => {

        this.productos = data;
      });
  }

  listarMovimientos(): void {

    this.movimientoService
      .listar()
      .subscribe(data => {

        this.movimientos = data;
this.movimientosFiltrados = data;
      });
  }

  registrarEntrada(): void {

    this.mensajeError = '';

    this.mensajeExito = '';

    if (!this.productoSeleccionado) {

      this.mensajeError =
        'Debe seleccionar un producto.';

      return;
    }

    if (!this.cantidad || this.cantidad <= 0) {

      this.mensajeError =
        'La cantidad debe ser mayor a cero.';

      return;
    }
    if (this.movimientoEditando) {

  this.movimientoService
    .actualizar(
      this.movimientoEditando.id,
      this.cantidad
    )
    .subscribe({

      next: () => {

        this.mensajeExito =
          'Movimiento actualizado correctamente';

        this.actualizarPantalla();

        this.movimientoEditando = null;
      },

      error: () => {

        this.mensajeError =
          'No se pudo actualizar el movimiento';
      }
    });

  return;
}

    this.movimientoService
      .registrarEntrada(
        this.productoSeleccionado.id!,
        this.cantidad,
        this.usuarioId
      )
      .subscribe(() => {
        this.registrarAuditoria(
    `CREÓ una ENTRADA de ${this.cantidad} unidades del producto ${this.productoSeleccionado?.nombre}`
  );

        this.mensajeExito =
          'Entrada registrada correctamente.';

        this.actualizarPantalla();
        
      });
}

  registrarSalida(): void {

    this.mensajeError = '';

    this.mensajeExito = '';

    if (!this.productoSeleccionado) {

      this.mensajeError =
        'Debe seleccionar un producto.';

      return;
    }

    if (!this.cantidad || this.cantidad <= 0) {

      this.mensajeError =
        'La cantidad debe ser mayor a cero.';

      return;
    }
    if (this.movimientoEditando) {

  this.movimientoService
    .actualizar(
      this.movimientoEditando.id,
      this.cantidad
    )
    .subscribe({

      next: () => {

        this.registrarAuditoria(
  `EDITÓ el movimiento ID ${this.movimientoEditando.id}`
);
        this.mensajeExito =
          'Movimiento actualizado correctamente';

        this.actualizarPantalla();

        this.movimientoEditando = null;
      },

      error: () => {

        this.mensajeError =
          'No se pudo actualizar el movimiento';
      }
    });

  return;
}

    this.movimientoService
      .registrarSalida(
        this.productoSeleccionado.id!,
        this.cantidad,
        this.usuarioId
      )
      .subscribe({

        next: () => {

  this.registrarAuditoria(
    `CREÓ una SALIDA de ${this.cantidad} unidades del producto ${this.productoSeleccionado?.nombre}`
  );

  this.mensajeExito =
    'Salida registrada correctamente.';

  this.actualizarPantalla();
},

        error: () => {

          this.mensajeError =
            'Stock insuficiente para realizar la salida.';
        }

      });
}

  actualizarPantalla(): void {

    this.listarProductos();

    this.listarMovimientos();

    this.cantidad = 1;

    this.productoSeleccionado = undefined;
      this.movimientoEditando = null;


    setTimeout(() => {

      this.mensajeExito = '';

    }, 3000);
}
eliminar(id: number): void {
  const movimiento =
  this.movimientos.find(m => m.id === id);

  if(!this.puedeEliminarMovimiento()){

    alert(
      'Solo el administrador puede eliminar movimientos.'
    );

    return;
  }

  if(confirm('¿Desea eliminar este movimiento?')){

    this.movimientoService
      .eliminar(id)
      .subscribe(() => {
        this.registrarAuditoria(
  `ELIMINÓ el movimiento ID ${id} correspondiente a ${movimiento?.tipo} del producto ${movimiento?.producto}`
);

        alert('Movimiento eliminado');

        this.listarMovimientos();

        this.listarProductos();

      });
  }
}

editar(m: any): void {
  if(!this.puedeEditarMovimiento()){

    alert(
      'No tiene permisos para editar movimientos.'
    );

    return;
  }


  this.movimientoEditando = m;

  this.cantidad = m.cantidad;

  this.productoSeleccionado =
    this.productos.find(
      p => p.nombre === m.producto
    );
}


cancelarEdicion(): void {

  this.movimientoEditando = null;

  this.productoSeleccionado = undefined;

  this.cantidad = 1;

  this.mensajeError = '';

  this.mensajeExito = '';
}
guardarEdicion(): void {

  if (!this.movimientoEditando) {
    return;
  }

  this.movimientoService
    .actualizar(
      this.movimientoEditando.id,
      this.cantidad
    )
    .subscribe({

      next: () => {
        this.registrarAuditoria(
    `EDITÓ el movimiento ID ${this.movimientoEditando.id} del producto ${this.productoSeleccionado?.nombre}. Nueva cantidad: ${this.cantidad}`
  );

        this.mensajeExito =
          'Movimiento actualizado correctamente';

        this.movimientoEditando = null;

        this.cantidad = 1;

        this.productoSeleccionado = undefined;

        this.listarMovimientos();

        this.listarProductos();
      },

      error: () => {

        this.mensajeError =
          'No se pudo actualizar el movimiento';
      }

    });
}
registrarAuditoria(
  accion: string
): void {

  const usuarioGuardado =
    localStorage.getItem('usuario');

  if (!usuarioGuardado) return;

  const usuario =
    JSON.parse(usuarioGuardado);

  const auditoria: any = {

    accion: accion,

    usuario: {
      id: usuario.id
    }

  };

  this.auditoriaService
    .crear(auditoria)
    .subscribe();
}

puedeEditarMovimiento(): boolean {

  return this.rol === 'ADMINISTRADOR' ||
         this.rol === 'GERENTE';
}

puedeEliminarMovimiento(): boolean {

  return this.rol === 'ADMINISTRADOR';
}

puedeVerAuditoria(): boolean {

  return this.rol === 'ADMINISTRADOR' ||
         this.rol === 'GERENTE';
}
aplicarFiltros(): void {

  this.movimientoService
    .filtrar(
      this.fechaInicio,
      this.fechaFin,
      this.tipoFiltro
    )
    .subscribe(data => {

      this.movimientosFiltrados = data;

    });
}
}
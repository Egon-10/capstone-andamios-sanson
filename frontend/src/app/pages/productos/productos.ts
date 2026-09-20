import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditoriaService }
from '../../services/auditoria.service';
import { Producto } from '../../models/producto';
import { Categoria } from '../../models/categoria';
import { Proveedor } from '../../models/proveedor';

import { ProductoService } from '../../services/producto.service';
import { CategoriaService } from '../../services/categoria.service';
import { ProveedorService } from '../../services/proveedor.service';

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './productos.html',
  styleUrl: './productos.css'
})
export class ProductosComponent implements OnInit {

  rol: string = '';

  productos: Producto[] = [];
  productosFiltrados: Producto[] = [];

textoBusqueda: string = '';
categoriaFiltro: string = '';

proveedorFiltro: string = '';

  categorias: Categoria[] = [];

  proveedores: Proveedor[] = [];

  producto: Producto = {
    nombre: '',
    descripcion: '',
    precio: 0,
    stock: 0,
    stockMinimo: 0
  };

  editando = false;
  mensajeError: string = '';
  

  constructor(
  private productoService: ProductoService,
  private categoriaService: CategoriaService,
  private proveedorService: ProveedorService,
  private auditoriaService: AuditoriaService
) {}

  ngOnInit(): void {

    const usuario = localStorage.getItem('usuario');

    if (usuario) {

      this.rol = JSON.parse(usuario).rol.nombre;
    }

    this.listarProductos();
    this.cargarCategorias();
    this.cargarProveedores();
  }

  listarProductos(): void {

  this.productoService
    .listar()
    .subscribe(data => {

      this.productos = data;

this.aplicarFiltros();
    });
}
aplicarFiltros(): void {

  const texto = this.textoBusqueda
    .toLowerCase()
    .trim();

  this.productosFiltrados = this.productos.filter(p => {

    const coincideTexto =

      !texto ||

      p.nombre?.toLowerCase().includes(texto) ||

      p.descripcion?.toLowerCase().includes(texto);

    const coincideCategoria =

      !this.categoriaFiltro ||

      p.categoria?.nombre === this.categoriaFiltro;

    const coincideProveedor =

      !this.proveedorFiltro ||

      p.proveedor?.nombre === this.proveedorFiltro;

    return coincideTexto &&
           coincideCategoria &&
           coincideProveedor;
  });
}

  cargarCategorias(): void {

    this.categoriaService
      .listar()
      .subscribe(data => {

        this.categorias = data;
      });
  }

  cargarProveedores(): void {

    this.proveedorService
      .listar()
      .subscribe(data => {

        this.proveedores = data;
      });
  }

  guardar(): void {

  if (!this.puedeGuardar()) {

    this.mensajeError =
      'No tiene permisos para realizar esta acción.';

    return;
  }

  if (!this.producto.nombre?.trim()) {

    this.mensajeError =
      'Debe ingresar el nombre del producto.';

    return;
  }

  if (!this.producto.descripcion?.trim()) {

    this.mensajeError =
      'Debe ingresar la descripción del producto.';

    return;
  }

  if (this.producto.precio <= 0) {

    this.mensajeError =
      'El precio debe ser mayor que cero.';

    return;
  }

  if (
  this.producto.stock === null ||
  this.producto.stock === undefined ||
  this.producto.stock.toString().trim() === ''
) {

  this.mensajeError =
    'Debe ingresar el stock del producto.';

  return;
}

if (this.producto.stock < 0) {

  this.mensajeError =
    'El stock no puede ser negativo.';

  return;
}

if (
  this.producto.stockMinimo === null ||
  this.producto.stockMinimo === undefined ||
  this.producto.stockMinimo.toString().trim() === ''
) {

  this.mensajeError =
    'Debe ingresar el stock mínimo del producto.';

  return;
}

if (this.producto.stockMinimo < 0) {

  this.mensajeError =
    'El stock mínimo no puede ser negativo.';

  return;
}

  if (!this.producto.categoria) {

    this.mensajeError =
      'Debe seleccionar una categoría.';

    return;
  }

  if (!this.producto.proveedor) {

    this.mensajeError =
      'Debe seleccionar un proveedor.';

    return;
  }

  /* Si todo está correcto */
  this.mensajeError = '';

  if (this.editando) {

  const nombreProducto = this.producto.nombre;

  this.productoService
    .actualizar(
      this.producto.id!,
      this.producto
    )
    .subscribe(() => {

      this.registrarAuditoria(
        'Editó el producto: ' +
        nombreProducto
      );

      this.listarProductos();

      this.limpiar();

    });

} else {

    const nombreProducto = this.producto.nombre;

this.productoService
  .crear(this.producto)
  .subscribe(() => {

    this.registrarAuditoria(
      'Creó el producto: ' +
      nombreProducto
    );

    this.listarProductos();

    this.limpiar();

  });
  }
}

  editar(producto: Producto): void {

    if (!this.puedeEditar()) {

      alert('No tiene permisos para editar productos.');

      return;
    }

    this.producto = {
      ...producto
    };

    this.editando = true;
  }

  eliminar(id: number): void {

  if (!this.esAdministrador()) {

    alert(
      'Solo el administrador puede eliminar productos.'
    );

    return;
  }

  const productoEliminar =
    this.productos.find(
      p => p.id === id
    );

  if (confirm('¿Desea eliminar el producto?')) {

    this.productoService
      .eliminar(id)
      .subscribe(() => {

        this.registrarAuditoria(
          'Eliminó el producto: ' +
          productoEliminar?.nombre
        );

        this.listarProductos();

      });
  }
}

  limpiar(): void {

  this.producto = {

    nombre: '',
    descripcion: '',
    precio: 0,
    stock: 0,
    stockMinimo: 0

  };

  this.editando = false;

  this.mensajeError = '';
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
  /* ======== MÉTODOS DE ROLES ======== */

  esAdministrador(): boolean {

    return this.rol === 'ADMINISTRADOR';
  }

  esGerente(): boolean {

    return this.rol === 'GERENTE';
  }

  esEncargado(): boolean {

    return this.rol === 'ENCARGADO';
  }

  puedeGuardar(): boolean {

    return this.esAdministrador() || this.esGerente();
  }

  puedeEditar(): boolean {

    return this.esAdministrador() || this.esGerente();
  }
  

}
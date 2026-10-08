import { Component, HostListener, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorApi } from '../../models/respuesta-login';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Producto } from '../../models/producto';
import { Categoria } from '../../models/categoria';
import { Proveedor } from '../../models/proveedor';

import { ProductoService } from '../../services/producto.service';
import { Pagina, ProductoResumen } from '../../models/pagina';
import { FichaProducto } from '../../models/ficha-producto';
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

  /**
   * HU-21: la lista la pagina el servidor.
   *
   * Antes se traia el catalogo completo y se filtraba en memoria, lo que
   * funcionaba mientras el catalogo era pequeno y dejaba de funcionar justo
   * cuando la busqueda empieza a hacer falta.
   */
  pagina?: Pagina<ProductoResumen>;
  cargandoLista = false;

  textoBusqueda = '';
  categoriaFiltro?: number;
  proveedorFiltro?: number;
  soloPorReponer = false;

  tamanoPagina = 20;
  orden = 'nombre';
  direccion: 'asc' | 'desc' = 'asc';

  /** HU-22: ficha abierta en el cuadro de detalle. */
  ficha?: FichaProducto;
  cargandoFicha = false;

  /** HU-20: umbrales que se estan editando dentro de la ficha. */
  umbrales = { stockMinimo: undefined as number | undefined,
               puntoReposicion: undefined as number | undefined,
               stockMaximo: undefined as number | undefined };
  guardandoUmbrales = false;
  mensajeFicha = '';
  errorFicha = '';

  categorias: Categoria[] = [];

  proveedores: Proveedor[] = [];

  producto: Producto = {
    sku: '',
    nombre: '',
    descripcion: '',
    precio: 0,
    stock: 0,
    stockMinimo: 0
  };

  editando = false;
  mensajeError: string = '';
  

  constructor(
  private readonly productoService: ProductoService,
  private readonly categoriaService: CategoriaService,
  private readonly proveedorService: ProveedorService
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
    this.aplicarFiltros();
  }

  /**
   * Pide al servidor la pagina que corresponde a los filtros actuales.
   * Cualquier cambio de filtro vuelve a la primera pagina: quedarse en la
   * pagina cinco de un resultado que ahora tiene dos mostraria una tabla
   * vacia sin explicacion.
   */
  aplicarFiltros(reiniciarPagina = true): void {
    if (reiniciarPagina && this.pagina) {
      this.pagina = { ...this.pagina, pagina: 0 };
    }

    this.cargandoLista = true;
    this.productoService
      .buscar({
        q: this.textoBusqueda.trim() || undefined,
        categoriaId: this.categoriaFiltro,
        proveedorId: this.proveedorFiltro,
        soloActivos: true,
        porReponer: this.soloPorReponer,
        pagina: reiniciarPagina ? 0 : (this.pagina?.pagina ?? 0),
        tamano: this.tamanoPagina,
        orden: this.orden,
        direccion: this.direccion
      })
      .subscribe({
        next: p => {
          this.cargandoLista = false;
          this.pagina = p;
        },
        error: (e: HttpErrorResponse) => {
          this.cargandoLista = false;
          this.mensajeError = e.error?.mensaje || 'No se pudo cargar el catalogo';
        }
      });
  }

  irAPagina(numero: number): void {
    if (!this.pagina || numero < 0 || numero >= this.pagina.totalPaginas) {
      return;
    }
    this.pagina = { ...this.pagina, pagina: numero };
    this.aplicarFiltros(false);
  }

  /** Ordena por una columna, y alterna el sentido si ya estaba ordenada por ella. */
  ordenarPor(campo: string): void {
    if (this.orden === campo) {
      this.direccion = this.direccion === 'asc' ? 'desc' : 'asc';
    } else {
      this.orden = campo;
      this.direccion = 'asc';
    }
    this.aplicarFiltros();
  }

  limpiarFiltros(): void {
    this.textoBusqueda = '';
    this.categoriaFiltro = undefined;
    this.proveedorFiltro = undefined;
    this.soloPorReponer = false;
    this.aplicarFiltros();
  }

  /** Numeros de pagina a mostrar: una ventana alrededor de la actual. */
  get paginasVisibles(): number[] {
    if (!this.pagina) {
      return [];
    }
    const total = this.pagina.totalPaginas;
    const actual = this.pagina.pagina;
    const desde = Math.max(0, Math.min(actual - 2, total - 5));
    const hasta = Math.min(total, desde + 5);
    const numeros: number[] = [];
    for (let i = desde; i < hasta; i++) {
      numeros.push(i);
    }
    return numeros;
  }

  // ---------------- HU-22: ficha de detalle ----------------

  verFicha(id: number): void {
    this.cargandoFicha = true;
    this.mensajeFicha = '';
    this.errorFicha = '';

    this.productoService.ficha(id).subscribe({
      next: f => {
        this.cargandoFicha = false;
        this.ficha = f;
        this.umbrales = {
          stockMinimo: f.existencias?.stockMinimo,
          puntoReposicion: f.existencias?.puntoReposicion,
          stockMaximo: f.existencias?.stockMaximo
        };
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoFicha = false;
        this.mensajeError = e.error?.mensaje || 'No se pudo cargar la ficha';
      }
    });
  }

  /** La ficha se cierra con Escape, ademas del boton. */
  @HostListener('document:keydown.escape')
  alPresionarEscape(): void {
    if (this.ficha) {
      this.cerrarFicha();
    }
  }

  /** Estado de orden de una columna, para los lectores de pantalla. */
  ariaOrden(campo: string): 'ascending' | 'descending' | 'none' {
    if (this.orden !== campo) {
      return 'none';
    }
    return this.direccion === 'asc' ? 'ascending' : 'descending';
  }

  cerrarFicha(): void {
    this.ficha = undefined;
    this.mensajeFicha = '';
    this.errorFicha = '';
  }

  /** HU-20: guarda los umbrales sin reenviar el resto de la ficha. */
  guardarUmbrales(): void {
    if (!this.ficha?.id) {
      return;
    }

    this.guardandoUmbrales = true;
    this.mensajeFicha = '';
    this.errorFicha = '';

    this.productoService.actualizarUmbrales(this.ficha.id, this.umbrales).subscribe({
      next: () => {
        this.guardandoUmbrales = false;
        this.mensajeFicha = 'Umbrales actualizados';
        // Se recarga la ficha para que la alerta de reposicion refleje el
        // umbral nuevo, que es el dato que se acaba de cambiar.
        this.verFicha(this.ficha!.id!);
        this.aplicarFiltros(false);
      },
      error: (e: HttpErrorResponse) => {
        this.guardandoUmbrales = false;
        const cuerpo = e.error as ErrorApi | null;
        const detalle = cuerpo?.errores ? Object.values(cuerpo.errores)[0] : undefined;
        this.errorFicha = detalle ?? cuerpo?.mensaje ?? 'No se pudieron guardar los umbrales';
      }
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

  if (!/^[A-Za-z0-9-]{3,20}$/.test(this.producto.sku?.trim() ?? '')) {

    this.mensajeError =
      'El SKU debe tener entre 3 y 20 caracteres: letras, números o guiones.';

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
    .subscribe({
      next: () => {


        this.listarProductos();

        this.limpiar();
      },
      error: (e: HttpErrorResponse) => this.mostrarErrorServidor(e)
    });

} else {

    const nombreProducto = this.producto.nombre;

this.productoService
  .crear(this.producto)
  .subscribe({
    next: () => {


      this.listarProductos();

      this.limpiar();
    },
    error: (e: HttpErrorResponse) => this.mostrarErrorServidor(e)
  });
  }
}

  /**
   * La fila de la tabla es un resumen y no el producto completo, de modo que
   * se pide la entidad por su identificador antes de llenar el formulario. Si
   * se copiara el resumen, los campos que no incluye se enviarian vacios al
   * guardar y se perderian sin aviso.
   */
  editar(resumen: ProductoResumen): void {

    if (!this.puedeEditar()) {

      alert('No tiene permisos para editar productos.');

      return;
    }

    this.productoService.buscarPorId(resumen.id!).subscribe({
      next: producto => {
        this.producto = { ...producto };
        this.editando = true;
        this.mensajeError = '';
      },
      error: (e: HttpErrorResponse) =>
        (this.mensajeError = e.error?.mensaje || 'No se pudo cargar el producto')
    });
  }

  eliminar(id: number): void {

  if (!this.esAdministrador()) {

    alert(
      'Solo el administrador puede eliminar productos.'
    );

    return;
  }

  const productoEliminar =
    this.pagina?.contenido.find(
      p => p.id === id
    );

  if (confirm('¿Desea eliminar el producto?')) {

    this.productoService
      .eliminar(id)
      .subscribe(() => {


        this.listarProductos();

      });
  }
}

  limpiar(): void {

  this.producto = {

    sku: '',
    nombre: '',
    descripcion: '',
    precio: 0,
    stock: 0,
    stockMinimo: 0

  };

  this.editando = false;

  this.mensajeError = '';
}

// La auditoria del catalogo la registra el servidor (correccion DEF-01).

  /** Muestra el mensaje de validación que devuelve el servidor (HU-10). */
  private mostrarErrorServidor(error: HttpErrorResponse): void {

    const cuerpo = error.error as ErrorApi | null;
    const detalle = cuerpo?.errores ? Object.values(cuerpo.errores)[0] : undefined;

    this.mensajeError =
      detalle ?? cuerpo?.mensaje ?? 'No se pudo guardar el producto.';
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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { ProductosComponent } from './productos';
import { ProductoService } from '../../services/producto.service';
import { CategoriaService } from '../../services/categoria.service';
import { ProveedorService } from '../../services/proveedor.service';
import { Pagina, ProductoResumen } from '../../models/pagina';
import { FichaProducto } from '../../models/ficha-producto';
import { Producto } from '../../models/producto';

/** CP-21 a CP-23: catálogo paginado (HU-21), ficha (HU-22) y umbrales (HU-20). */
describe('ProductosComponent', () => {
  let fixture: ComponentFixture<ProductosComponent>;
  let c: ProductosComponent;
  let productos: jasmine.SpyObj<ProductoService>;

  function pagina(numero: number, totalPaginas: number): Pagina<ProductoResumen> {
    return {
      contenido: [{ id: 1, sku: 'AND-1', nombre: 'Marco', stock: 5, necesitaReposicion: true }],
      pagina: numero, tamano: 20, totalElementos: totalPaginas * 20, totalPaginas,
      primera: numero === 0, ultima: numero === totalPaginas - 1
    };
  }

  const ficha: FichaProducto = {
    id: 1, nombre: 'Marco',
    existencias: { stock: 5, stockMinimo: 10, puntoReposicion: 15, stockMaximo: 200 },
    ultimosMovimientos: []
  };

  function error(cuerpo: unknown): HttpErrorResponse {
    return new HttpErrorResponse({ status: 422, error: cuerpo });
  }

  beforeEach(() => {
    localStorage.setItem('usuario', JSON.stringify({ id: 1, rol: { nombre: 'GERENTE' } }));
    productos = jasmine.createSpyObj('ProductoService',
      ['buscar', 'ficha', 'actualizarUmbrales', 'buscarPorId', 'crear', 'actualizar', 'eliminar']);
    productos.buscar.and.returnValue(of(pagina(0, 10)));
    const categorias = jasmine.createSpyObj('CategoriaService', ['listar']);
    categorias.listar.and.returnValue(of([]));
    const proveedores = jasmine.createSpyObj('ProveedorService', ['listar']);
    proveedores.listar.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [ProductosComponent],
      providers: [
        { provide: ProductoService, useValue: productos },
        { provide: CategoriaService, useValue: categorias },
        { provide: ProveedorService, useValue: proveedores }
      ]
    });
    fixture = TestBed.createComponent(ProductosComponent);
    c = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => localStorage.clear());

  it('HU-21: pide la primera pagina al servidor al iniciar', () => {
    const filtros = productos.buscar.calls.mostRecent().args[0];
    expect(filtros.pagina).toBe(0);
    expect(filtros.soloActivos).toBeTrue();
    expect(c.pagina?.totalPaginas).toBe(10);
  });

  it('HU-21: envia los filtros al servidor y vuelve a la primera pagina', () => {
    c.pagina = pagina(4, 10);
    c.textoBusqueda = '  marco ';
    c.categoriaFiltro = 3;
    c.soloPorReponer = true;

    c.aplicarFiltros();

    const filtros = productos.buscar.calls.mostRecent().args[0];
    expect(filtros.q).toBe('marco');
    expect(filtros.categoriaId).toBe(3);
    expect(filtros.porReponer).toBeTrue();
    expect(filtros.pagina).toBe(0);
  });

  it('HU-21: navega entre paginas sin salirse del rango', () => {
    productos.buscar.calls.reset();
    c.irAPagina(3);
    expect(productos.buscar.calls.mostRecent().args[0].pagina).toBe(3);

    productos.buscar.calls.reset();
    c.irAPagina(-1);
    c.irAPagina(10);
    expect(productos.buscar).not.toHaveBeenCalled();
  });

  it('HU-21: alterna el sentido al ordenar dos veces por la misma columna', () => {
    c.ordenarPor('stock');
    expect(c.orden).toBe('stock');
    expect(c.direccion).toBe('asc');
    expect(c.ariaOrden('stock')).toBe('ascending');

    c.ordenarPor('stock');
    expect(c.direccion).toBe('desc');
    expect(c.ariaOrden('stock')).toBe('descending');
    expect(c.ariaOrden('nombre')).toBe('none');

    c.ordenarPor('precio');
    expect(c.direccion).toBe('asc');
  });

  it('HU-21: limpia todos los filtros', () => {
    c.textoBusqueda = 'x';
    c.categoriaFiltro = 2;
    c.proveedorFiltro = 4;
    c.soloPorReponer = true;
    c.limpiarFiltros();
    expect(c.textoBusqueda).toBe('');
    expect(c.categoriaFiltro).toBeUndefined();
    expect(c.proveedorFiltro).toBeUndefined();
    expect(c.soloPorReponer).toBeFalse();
  });

  it('HU-21: muestra una ventana de cinco paginas alrededor de la actual', () => {
    c.pagina = pagina(5, 10);
    expect(c.paginasVisibles).toEqual([3, 4, 5, 6, 7]);
    c.pagina = pagina(0, 10);
    expect(c.paginasVisibles).toEqual([0, 1, 2, 3, 4]);
    c.pagina = pagina(9, 10);
    expect(c.paginasVisibles).toEqual([5, 6, 7, 8, 9]);
    c.pagina = pagina(0, 2);
    expect(c.paginasVisibles).toEqual([0, 1]);
    c.pagina = undefined;
    expect(c.paginasVisibles).toEqual([]);
  });

  it('HU-21: informa si el catalogo no se pudo cargar', () => {
    productos.buscar.and.returnValue(throwError(() => error({ mensaje: 'Sin conexion' })));
    c.aplicarFiltros();
    expect(c.mensajeError).toBe('Sin conexion');
    expect(c.cargandoLista).toBeFalse();
  });

  it('HU-22: abre la ficha con sus umbrales cargados', () => {
    productos.ficha.and.returnValue(of(ficha));
    c.verFicha(1);
    expect(c.ficha).toEqual(ficha);
    expect(c.umbrales.puntoReposicion).toBe(15);
    expect(c.umbrales.stockMaximo).toBe(200);
  });

  it('HU-22: informa si la ficha no se pudo cargar', () => {
    productos.ficha.and.returnValue(throwError(() => error({ mensaje: 'Producto no encontrado' })));
    c.verFicha(99);
    expect(c.mensajeError).toBe('Producto no encontrado');
    expect(c.ficha).toBeUndefined();
  });

  it('HU-22: la ficha se cierra con Escape', () => {
    c.ficha = ficha;
    c.alPresionarEscape();
    expect(c.ficha).toBeUndefined();
    c.alPresionarEscape();
    expect(c.ficha).toBeUndefined();
  });

  it('HU-20: guarda solo los umbrales y recarga la ficha', () => {
    productos.ficha.and.returnValue(of(ficha));
    productos.actualizarUmbrales.and.returnValue(of({ id: 1 } as Producto));
    c.verFicha(1);
    c.umbrales.puntoReposicion = 20;

    c.guardarUmbrales();

    expect(productos.actualizarUmbrales).toHaveBeenCalledWith(1,
      { stockMinimo: 10, puntoReposicion: 20, stockMaximo: 200 });
    expect(productos.ficha).toHaveBeenCalledTimes(2);
  });

  it('HU-20: muestra el campo que rechazo el servidor', () => {
    productos.ficha.and.returnValue(of(ficha));
    productos.actualizarUmbrales.and.returnValue(throwError(() =>
      error({ mensaje: 'Datos invalidos', errores: { stockMaximo: 'El maximo no puede ser menor' } })));
    c.verFicha(1);

    c.guardarUmbrales();

    expect(c.errorFicha).toBe('El maximo no puede ser menor');
    expect(c.guardandoUmbrales).toBeFalse();
  });

  it('HU-20: sin ficha abierta no guarda nada', () => {
    c.guardarUmbrales();
    expect(productos.actualizarUmbrales).not.toHaveBeenCalled();
  });

  it('editar pide el producto completo antes de llenar el formulario', () => {
    productos.buscarPorId.and.returnValue(of({
      id: 1, sku: 'AND-1', nombre: 'Marco', descripcion: 'Galvanizado',
      precio: 300, stock: 5, stockMinimo: 10
    }));

    c.editar({ id: 1, nombre: 'Marco' });

    expect(productos.buscarPorId).toHaveBeenCalledWith(1);
    expect(c.producto.descripcion).toBe('Galvanizado');
    expect(c.editando).toBeTrue();
  });

  it('editar informa si no pudo cargar el producto', () => {
    productos.buscarPorId.and.returnValue(throwError(() => error({ mensaje: 'No encontrado' })));
    c.editar({ id: 1 });
    expect(c.mensajeError).toBe('No encontrado');
    expect(c.editando).toBeFalse();
  });
});

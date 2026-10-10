import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { KardexComponent } from './kardex';
import { ProductoService } from '../../services/producto.service';
import { InventarioService } from '../../services/inventario.service';
import { Kardex } from '../../models/kardex';

/** CP-19: pantalla del kardex (HU-18). */
describe('KardexComponent', () => {
  let fixture: ComponentFixture<KardexComponent>;
  let c: KardexComponent;
  let inventario: jasmine.SpyObj<InventarioService>;

  const kardex: Kardex = {
    productoId: 5, stockActual: 35, saldoInicial: 250,
    lineas: [
      { id: 4, tipo: 'SALIDA', salida: 210, saldo: 40, estado: 'REGISTRADO' },
      { id: 5, tipo: 'SALIDA', salida: 5, saldo: 35, estado: 'REGISTRADO' }
    ]
  };

  beforeEach(() => {
    const productos = jasmine.createSpyObj('ProductoService', ['listar']);
    productos.listar.and.returnValue(of([]));
    inventario = jasmine.createSpyObj('InventarioService', ['kardex']);

    TestBed.configureTestingModule({
      imports: [KardexComponent],
      providers: [
        { provide: ProductoService, useValue: productos },
        { provide: InventarioService, useValue: inventario }
      ]
    });
    fixture = TestBed.createComponent(KardexComponent);
    c = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('sin producto no consulta nada', () => {
    c.consultar();
    expect(inventario.kardex).not.toHaveBeenCalled();
    expect(c.kardex).toBeUndefined();
  });

  it('consulta el kardex del producto con las fechas elegidas', () => {
    inventario.kardex.and.returnValue(of(kardex));
    c.productoId = 5;
    c.desde = '2026-01-01';

    c.consultar();

    expect(inventario.kardex).toHaveBeenCalledWith(5, '2026-01-01', undefined);
    expect(c.kardex).toEqual(kardex);
    expect(c.cargando).toBeFalse();
  });

  it('muestra el error del servidor', () => {
    inventario.kardex.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 404, error: { mensaje: 'Producto no encontrado' } })));
    c.productoId = 99;

    c.consultar();

    expect(c.mensajeError).toBe('Producto no encontrado');
    expect(c.kardex).toBeUndefined();
  });

  it('usa un mensaje por omision si el servidor no envia uno', () => {
    inventario.kardex.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.productoId = 5;
    c.consultar();
    expect(c.mensajeError).toBe('No se pudo cargar el kardex');
  });

  it('limpiar fechas vuelve a consultar el historial completo', () => {
    inventario.kardex.and.returnValue(of(kardex));
    c.productoId = 5;
    c.desde = '2026-01-01';
    c.hasta = '2026-02-01';

    c.limpiarFechas();

    expect(inventario.kardex).toHaveBeenCalledWith(5, undefined, undefined);
  });

  it('comprueba que la ultima linea cuadre con el stock', () => {
    expect(c.kardexCuadra).toBeTrue();

    c.kardex = kardex;
    expect(c.kardexCuadra).toBeTrue();

    c.kardex = { ...kardex, stockActual: 30 };
    expect(c.kardexCuadra).toBeFalse();

    // Con un rango de fechas la ultima linea no es la ultima del producto.
    c.desde = '2026-01-01';
    expect(c.kardexCuadra).toBeTrue();

    c.desde = '';
    c.kardex = { ...kardex, lineas: [] };
    expect(c.kardexCuadra).toBeTrue();
  });

  it('HU-40: sin producto elegido invita a elegir uno y no lo trata como error', () => {
    expect(c.estadoVista).toBe('vacio');
    const vista: HTMLElement = fixture.nativeElement;
    expect(vista.textContent).toContain('Seleccione un producto para ver su kardex');
    expect(vista.querySelector('[role="alert"]')).toBeNull();
  });

  it('HU-40: si la consulta falla muestra el error y reintentar vuelve a consultar', () => {
    inventario.kardex.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.productoId = 5;
    c.consultar();
    fixture.detectChanges();

    expect(c.estadoVista).toBe('error');
    const alerta: HTMLElement = fixture.nativeElement.querySelector('app-estado-vista [role="alert"]');
    expect(alerta.textContent).toContain('No se pudo cargar el kardex');

    inventario.kardex.and.returnValue(of({ ...kardex, lineas: [] }));
    alerta.querySelector('button')!.click();
    fixture.detectChanges();

    expect(inventario.kardex).toHaveBeenCalledTimes(2);
    expect(c.estadoVista).toBe('vacio');
    expect(fixture.nativeElement.textContent).toContain('todavía no tiene movimientos');
  });

  it('distingue visualmente los asientos anulados y compensatorios', () => {
    expect(c.claseEstado('ANULADO')).toBe('fila-anulada');
    expect(c.claseEstado('COMPENSACION')).toBe('fila-compensacion');
    expect(c.claseEstado('REGISTRADO')).toBe('');
    expect(c.claseEstado(undefined)).toBe('');
  });
});

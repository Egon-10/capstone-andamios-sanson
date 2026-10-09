import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { AjustesComponent } from './ajustes';
import { ProductoService } from '../../services/producto.service';
import { AjusteService } from '../../services/ajuste.service';
import { Producto } from '../../models/producto';
import { AlertaService } from '../../services/alerta.service';

/** CP-17: pantalla de ajustes por conteo físico (HU-16). */
describe('AjustesComponent', () => {
  let fixture: ComponentFixture<AjustesComponent>;
  let c: AjustesComponent;
  let productos: jasmine.SpyObj<ProductoService>;
  let ajustes: jasmine.SpyObj<AjusteService>;

  const rueda: Producto = {
    id: 5, sku: 'RUE-001', nombre: 'Rueda', descripcion: '', precio: 92, stock: 40, stockMinimo: 10
  };

  function sesion(rol: string): void {
    localStorage.setItem('usuario', JSON.stringify({ id: 1, rol: { nombre: rol } }));
  }

  function error(cuerpo: unknown): HttpErrorResponse {
    return new HttpErrorResponse({ status: 422, error: cuerpo });
  }

  beforeEach(() => {
    localStorage.clear();
    productos = jasmine.createSpyObj('ProductoService', ['listar']);
    ajustes = jasmine.createSpyObj('AjusteService', ['listar', 'registrar', 'aprobar', 'rechazar']);
    productos.listar.and.returnValue(of([rueda]));
    ajustes.listar.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [AjustesComponent],
      providers: [
        { provide: AlertaService, useValue: jasmine.createSpyObj('AlertaService', ['actualizar']) },
        { provide: ProductoService, useValue: productos },
        { provide: AjusteService, useValue: ajustes }
      ]
    });
  });

  afterEach(() => localStorage.clear());

  function crear(): void {
    fixture = TestBed.createComponent(AjustesComponent);
    c = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga productos y conteos al iniciar', () => {
    sesion('ENCARGADO');
    crear();
    expect(c.productos.length).toBe(1);
    expect(ajustes.listar).toHaveBeenCalledWith(undefined);
    expect(c.rol).toBe('ENCARGADO');
  });

  it('tolera un usuario guardado que no es JSON valido', () => {
    localStorage.setItem('usuario', '{roto');
    crear();
    expect(c.rol).toBe('');
  });

  it('filtra por estado al listar', () => {
    crear();
    c.filtroEstado = 'PENDIENTE';
    c.listar();
    expect(ajustes.listar).toHaveBeenCalledWith('PENDIENTE');
  });

  it('muestra el error del servidor si no puede listar', () => {
    ajustes.listar.and.returnValue(throwError(() => error({ mensaje: 'Sin conexion' })));
    crear();
    expect(c.mensajeError).toBe('Sin conexion');
  });

  it('HU-40: si la lista no carga muestra el error y reintentar vuelve a consultar', () => {
    ajustes.listar.and.returnValue(throwError(() => error({ mensaje: 'Sin conexion' })));
    crear();

    expect(c.estadoVista).toBe('error');
    const alerta: HTMLElement = fixture.nativeElement.querySelector('app-estado-vista [role="alert"]');
    expect(alerta.textContent).toContain('Sin conexion');

    ajustes.listar.and.returnValue(of([{ id: 9, estado: 'PENDIENTE' }]));
    alerta.querySelector('button')!.click();
    fixture.detectChanges();

    expect(ajustes.listar).toHaveBeenCalledTimes(2);
    expect(c.estadoVista).toBe('listo');
    expect(fixture.nativeElement.querySelector('table')).not.toBeNull();
  });

  it('HU-40: distingue la lista sin conteos de la que no tiene pendientes', () => {
    crear();
    expect(c.estadoVista).toBe('vacio');
    expect(fixture.nativeElement.textContent).toContain('Todavía no hay conteos registrados');

    c.filtroEstado = 'PENDIENTE';
    c.listar();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No hay conteos pendientes de aprobación');
  });

  it('calcula la diferencia en vivo con su sentido', () => {
    crear();
    expect(c.diferencia).toBeUndefined();
    expect(c.textoDiferencia).toBe('');

    c.productoSeleccionado = rueda;
    c.stockFisico = 35;
    expect(c.diferencia).toBe(-5);
    expect(c.textoDiferencia).toContain('Faltan 5');

    c.stockFisico = 48;
    expect(c.textoDiferencia).toContain('Sobran 8');

    c.stockFisico = 40;
    expect(c.textoDiferencia).toContain('coincide');
  });

  it('no envia el conteo si falta el producto, el numero o no hay diferencia', () => {
    crear();
    c.registrar();
    expect(c.errorCampo['productoId']).toBeDefined();

    c.productoSeleccionado = rueda;
    c.registrar();
    expect(c.errorCampo['stockFisico']).toBeDefined();

    c.stockFisico = 40;
    c.registrar();
    expect(c.errorCampo['stockFisico']).toContain('coincide');

    expect(ajustes.registrar).not.toHaveBeenCalled();
  });

  it('registra el conteo y deja el formulario limpio', () => {
    ajustes.registrar.and.returnValue(of({ id: 9, estado: 'PENDIENTE' }));
    crear();
    c.productoSeleccionado = rueda;
    c.stockFisico = 35;
    c.observacion = '  estante dos  ';

    c.registrar();

    expect(ajustes.registrar).toHaveBeenCalledWith(5, 35, 'estante dos');
    expect(c.mensajeExito).toContain('pendiente');
    expect(c.productoSeleccionado).toBeUndefined();
    expect(c.guardando).toBeFalse();
  });

  it('marca el campo que rechazo el servidor al registrar', () => {
    ajustes.registrar.and.returnValue(throwError(() =>
      error({ mensaje: 'Ya tiene un conteo pendiente', errores: { productoId: 'pendiente' } })));
    crear();
    c.productoSeleccionado = rueda;
    c.stockFisico = 35;

    c.registrar();

    expect(c.mensajeError).toBe('Ya tiene un conteo pendiente');
    expect(c.errorCampo['productoId']).toBe('pendiente');
  });

  it('aprueba y recarga productos, porque el stock cambio', () => {
    ajustes.aprobar.and.returnValue(of({ id: 9, movimientoId: 200 }));
    crear();
    productos.listar.calls.reset();

    c.aprobar({ id: 9 });

    expect(c.mensajeExito).toContain('200');
    expect(productos.listar).toHaveBeenCalled();
  });

  it('informa cuando el servidor rechaza una aprobacion vencida', () => {
    ajustes.aprobar.and.returnValue(throwError(() => error({ mensaje: 'Registre un recuento' })));
    crear();
    c.aprobar({ id: 9 });
    expect(c.mensajeError).toBe('Registre un recuento');
    expect(c.guardando).toBeFalse();
  });

  it('usa un mensaje por omision si el servidor no envia uno', () => {
    ajustes.aprobar.and.returnValue(throwError(() => error(null)));
    crear();
    c.aprobar({ id: 9 });
    expect(c.mensajeError).toBe('No se pudo aprobar el ajuste');
  });

  it('exige un motivo de al menos diez caracteres para rechazar', () => {
    crear();
    c.confirmarRechazo();
    expect(ajustes.rechazar).not.toHaveBeenCalled();

    c.abrirRechazo({ id: 9 });
    c.motivoRechazo = 'corto';
    c.confirmarRechazo();

    expect(c.errorCampo['motivo']).toBeDefined();
    expect(ajustes.rechazar).not.toHaveBeenCalled();
  });

  it('rechaza con el motivo y cierra el cuadro', () => {
    ajustes.rechazar.and.returnValue(of({ id: 9, estado: 'RECHAZADO' }));
    crear();
    c.abrirRechazo({ id: 9 });
    c.motivoRechazo = 'No se conto el deposito dos';

    c.confirmarRechazo();

    expect(ajustes.rechazar).toHaveBeenCalledWith(9, 'No se conto el deposito dos');
    expect(c.ajusteRechazando).toBeUndefined();
    expect(c.mensajeExito).toContain('no se modifico');
  });

  it('mantiene el cuadro abierto si el rechazo falla', () => {
    ajustes.rechazar.and.returnValue(throwError(() => error({ mensaje: 'Ya fue resuelto' })));
    crear();
    c.abrirRechazo({ id: 9 });
    c.motivoRechazo = 'No se conto el deposito dos';

    c.confirmarRechazo();

    expect(c.mensajeError).toBe('Ya fue resuelto');
    expect(c.ajusteRechazando).toBeDefined();

    c.cerrarRechazo();
    expect(c.ajusteRechazando).toBeUndefined();
  });

  it('solo el administrador y el gerente resuelven ajustes', () => {
    sesion('ENCARGADO');
    crear();
    expect(c.puedeResolver()).toBeFalse();
    c.rol = 'GERENTE';
    expect(c.puedeResolver()).toBeTrue();
    c.rol = 'ADMINISTRADOR';
    expect(c.puedeResolver()).toBeTrue();
  });
});

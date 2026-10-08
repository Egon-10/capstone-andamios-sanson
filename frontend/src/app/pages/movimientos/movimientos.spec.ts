import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { MovimientosComponent } from './movimientos';
import { ProductoService } from '../../services/producto.service';
import { MovimientoService } from '../../services/movimiento.service';
import { MotivoService } from '../../services/motivo.service';
import { Producto } from '../../models/producto';
import { Motivo } from '../../models/motivo';
import { AlertaService } from '../../services/alerta.service';

/** CP-13 a CP-16: pantalla de movimientos (HU-12 a HU-15). */
describe('MovimientosComponent', () => {
  let fixture: ComponentFixture<MovimientosComponent>;
  let c: MovimientosComponent;
  let movimientos: jasmine.SpyObj<MovimientoService>;
  let motivos: jasmine.SpyObj<MotivoService>;

  const marco: Producto = {
    id: 1, sku: 'AND-1', nombre: 'Marco', descripcion: '', precio: 300, stock: 100, stockMinimo: 10
  };
  const compra: Motivo = { codigo: 'COMPRA', nombre: 'Compra', aplicaA: 'ENTRADA', exigeNota: false };
  const merma: Motivo = { codigo: 'MERMA', nombre: 'Merma', aplicaA: 'SALIDA', exigeNota: true };

  function error(cuerpo: unknown): HttpErrorResponse {
    return new HttpErrorResponse({ status: 422, error: cuerpo });
  }

  beforeEach(() => {
    localStorage.clear();
    const productos = jasmine.createSpyObj('ProductoService', ['listar']);
    productos.listar.and.returnValue(of([marco]));
    movimientos = jasmine.createSpyObj('MovimientoService',
      ['listar', 'registrarEntrada', 'registrarSalida', 'anular', 'filtrar']);
    movimientos.listar.and.returnValue(of([{ id: 1, estado: 'REGISTRADO' }]));
    motivos = jasmine.createSpyObj('MotivoService', ['listar']);
    motivos.listar.and.callFake((tipo?: string) => of(tipo === 'SALIDA' ? [merma] : [compra]));

    TestBed.configureTestingModule({
      imports: [MovimientosComponent],
      providers: [
        { provide: AlertaService, useValue: jasmine.createSpyObj('AlertaService', ['actualizar']) },
        { provide: ProductoService, useValue: productos },
        { provide: MovimientoService, useValue: movimientos },
        { provide: MotivoService, useValue: motivos }
      ]
    });
  });

  afterEach(() => localStorage.clear());

  function crear(rol = 'GERENTE'): void {
    localStorage.setItem('usuario', JSON.stringify({ id: 7, rol: { nombre: rol } }));
    fixture = TestBed.createComponent(MovimientosComponent);
    c = fixture.componentInstance;
    fixture.detectChanges();
  }

  function formulario(motivo = 'COMPRA'): void {
    c.productoSeleccionado = marco;
    c.cantidad = 5;
    c.motivoSeleccionado = motivo;
  }

  it('carga productos, movimientos y los motivos de cada tipo', () => {
    crear();
    expect(c.productos.length).toBe(1);
    expect(c.movimientosFiltrados.length).toBe(1);
    expect(c.motivosEntrada).toEqual([compra]);
    expect(c.motivosSalida).toEqual([merma]);
  });

  it('deja vacias las listas de motivos si el catalogo no responde', () => {
    motivos.listar.and.returnValue(throwError(() => error(null)));
    crear();
    expect(c.motivosEntrada).toEqual([]);
    expect(c.motivosSalida).toEqual([]);
  });

  it('tolera un usuario guardado que no es JSON valido', () => {
    localStorage.setItem('usuario', '{roto');
    fixture = TestBed.createComponent(MovimientosComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.rol).toBe('');
  });

  it('ofrece los motivos del tipo elegido y reinicia el motivo al cambiar de tipo', () => {
    crear();
    expect(c.motivos).toEqual([compra]);
    expect(c.pideCosto).toBeTrue();

    c.motivoSeleccionado = 'COMPRA';
    c.cambiarTipo('SALIDA');

    expect(c.motivos).toEqual([merma]);
    expect(c.motivoSeleccionado).toBe('');
    expect(c.pideCosto).toBeFalse();
  });

  it('exige observacion cuando el motivo lo pide', () => {
    crear();
    c.cambiarTipo('SALIDA');
    formulario('MERMA');
    expect(c.exigeNota).toBeTrue();

    c.registrar();

    expect(c.errorCampo['observacion']).toBeDefined();
    expect(movimientos.registrarSalida).not.toHaveBeenCalled();
  });

  it('valida producto, cantidad y motivo antes de enviar', () => {
    crear();
    c.registrar();
    expect(c.errorCampo['productoId']).toBeDefined();

    c.productoSeleccionado = marco;
    c.cantidad = 0;
    c.registrar();
    expect(c.errorCampo['cantidad']).toBeDefined();

    c.cantidad = 3;
    c.registrar();
    expect(c.errorCampo['motivo']).toBeDefined();

    expect(movimientos.registrarEntrada).not.toHaveBeenCalled();
  });

  it('registra una entrada con su costo y sin identificar al usuario', () => {
    movimientos.registrarEntrada.and.returnValue(of({ id: 10 }));
    crear();
    formulario();
    c.costoUnitario = 310;
    c.observacion = '  factura 123  ';

    c.registrar();

    expect(movimientos.registrarEntrada).toHaveBeenCalledWith({
      productoId: 1, cantidad: 5, motivo: 'COMPRA', observacion: 'factura 123', costoUnitario: 310
    });
    expect(c.mensajeExito).toContain('Entrada');
    expect(c.productoSeleccionado).toBeUndefined();
  });

  it('registra una salida sin enviar costo', () => {
    movimientos.registrarSalida.and.returnValue(of({ id: 11 }));
    crear();
    c.cambiarTipo('SALIDA');
    formulario('MERMA');
    c.observacion = 'Dos planchas dobladas';
    c.costoUnitario = 999;

    c.registrar();

    const solicitud = movimientos.registrarSalida.calls.mostRecent().args[0];
    expect(solicitud.costoUnitario).toBeUndefined();
    expect(c.mensajeExito).toContain('Salida');
  });

  it('muestra el mensaje y el campo que rechazo el servidor', () => {
    movimientos.registrarEntrada.and.returnValue(throwError(() =>
      error({ mensaje: 'Stock insuficiente', errores: { cantidad: 'Stock insuficiente' } })));
    crear();
    formulario();

    c.registrar();

    expect(c.mensajeError).toBe('Stock insuficiente');
    expect(c.errorCampo['cantidad']).toBe('Stock insuficiente');
    expect(c.guardando).toBeFalse();
  });

  it('usa un mensaje por omision si el servidor no envia uno', () => {
    movimientos.registrarEntrada.and.returnValue(throwError(() => error(null)));
    crear();
    formulario();
    c.registrar();
    expect(c.mensajeError).toBe('No se pudo registrar el movimiento');
  });

  it('exige una justificacion de al menos diez caracteres para anular', () => {
    crear();
    c.confirmarAnulacion();
    expect(movimientos.anular).not.toHaveBeenCalled();

    c.abrirAnulacion({ id: 4 });
    c.justificacion = 'error';
    c.confirmarAnulacion();

    expect(c.errorCampo['justificacion']).toBeDefined();
    expect(movimientos.anular).not.toHaveBeenCalled();
  });

  it('anula con la justificacion y cierra el cuadro', () => {
    movimientos.anular.and.returnValue(of({ id: 12, estado: 'COMPENSACION' }));
    crear();
    c.abrirAnulacion({ id: 4 });
    c.justificacion = 'Se cargo a la obra equivocada';

    c.confirmarAnulacion();

    expect(movimientos.anular).toHaveBeenCalledWith(4, 'Se cargo a la obra equivocada');
    expect(c.movimientoAnulando).toBeUndefined();
    expect(c.mensajeExito).toContain('anulado');
  });

  it('informa si el servidor no permite anular', () => {
    movimientos.anular.and.returnValue(throwError(() => error({ mensaje: 'Ya fue anulado' })));
    crear();
    c.abrirAnulacion({ id: 4 });
    c.justificacion = 'Se cargo a la obra equivocada';

    c.confirmarAnulacion();

    expect(c.mensajeError).toBe('Ya fue anulado');
    c.cerrarAnulacion();
    expect(c.movimientoAnulando).toBeUndefined();
  });

  it('solo ofrece anular asientos vigentes y a quien tiene permiso', () => {
    crear('GERENTE');
    expect(c.sePuedeAnular({ estado: 'REGISTRADO' })).toBeTrue();
    expect(c.sePuedeAnular({ estado: 'ANULADO' })).toBeFalse();
    expect(c.sePuedeAnular({ estado: 'COMPENSACION' })).toBeFalse();

    c.rol = 'ENCARGADO';
    expect(c.sePuedeAnular({ estado: 'REGISTRADO' })).toBeFalse();
  });

  it('filtra en el servidor y limpia los filtros', () => {
    movimientos.filtrar.and.returnValue(of([]));
    crear();
    c.fechaInicio = '2026-06-01';
    c.tipoFiltro = 'SALIDA';

    c.aplicarFiltros();

    expect(movimientos.filtrar).toHaveBeenCalledWith('2026-06-01', '', 'SALIDA');
    expect(c.movimientosFiltrados).toEqual([]);

    c.limpiarFiltros();
    expect(c.tipoFiltro).toBe('');
    expect(c.movimientosFiltrados).toEqual(c.movimientos);
  });

  it('informa una fecha de filtro rechazada', () => {
    movimientos.filtrar.and.returnValue(throwError(() => error({ mensaje: 'Formato de fecha' })));
    crear();
    c.aplicarFiltros();
    expect(c.mensajeError).toBe('Formato de fecha');
  });

  it('distingue los asientos anulados y compensatorios', () => {
    crear();
    expect(c.claseEstado({ estado: 'ANULADO' })).toBe('fila-anulada');
    expect(c.claseEstado({ estado: 'COMPENSACION' })).toBe('fila-compensacion');
    expect(c.claseEstado({ estado: 'REGISTRADO' })).toBe('');
  });
});

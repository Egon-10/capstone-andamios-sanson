import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { ValorizacionComponent } from './valorizacion';
import { InventarioService } from '../../services/inventario.service';
import { Valorizacion } from '../../models/valorizacion';
import { ReporteService } from '../../services/reporte.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

/** CP-18: pantalla de valorización (HU-17) y reposición (HU-20). */
describe('ValorizacionComponent', () => {
  let inventario: jasmine.SpyObj<InventarioService>;

  const valorizacion: Valorizacion = {
    valorTotal: 41840, unidadesTotales: 170, productosContados: 2,
    porCategoria: [{ categoria: 'Andamios', productos: 1, unidades: 150, valor: 40000, participacion: 95.6 }],
    detalle: [
      { productoId: 1, sku: 'AND-1', producto: 'Marco "doble"', categoria: 'Andamios',
        stock: 150, costoPromedio: 266.67, valor: 40000, necesitaReposicion: false },
      { productoId: 2, sku: 'RUE-1', producto: 'Rueda', categoria: 'Ruedas',
        stock: 20, costoPromedio: 92, valor: 1840, necesitaReposicion: true }
    ]
  };

  function crear(): ValorizacionComponent {
    const fixture = TestBed.createComponent(ValorizacionComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    inventario = jasmine.createSpyObj('InventarioService', ['valorizacion', 'porReponer']);
    inventario.valorizacion.and.returnValue(of(valorizacion));
    inventario.porReponer.and.returnValue(of([valorizacion.detalle[1]]));
    TestBed.configureTestingModule({
      imports: [ValorizacionComponent],
      providers: [{ provide: InventarioService, useValue: inventario }, provideHttpClient(), provideHttpClientTesting()]
    });
  });

  it('carga la valorizacion y la lista de reposicion', () => {
    const c = crear();
    expect(c.valorizacion).toEqual(valorizacion);
    expect(c.porReponer.length).toBe(1);
    expect(c.cargando).toBeFalse();
  });

  it('muestra el error del servidor, por ejemplo al encargado sin permiso', () => {
    inventario.valorizacion.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 403, error: { mensaje: 'No tiene permisos' } })));
    inventario.porReponer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    const c = crear();

    expect(c.mensajeError).toBe('No tiene permisos');
    expect(c.porReponer).toEqual([]);
  });

  it('HU-40: si la primera carga falla muestra el error y reintentar vuelve a calcular', () => {
    inventario.valorizacion.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 500, error: { mensaje: 'Error interno' } })));
    const fixture = TestBed.createComponent(ValorizacionComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.estadoVista).toBe('error');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('No se pudo calcular la valorización');

    inventario.valorizacion.and.returnValue(of(valorizacion));
    (Array.from(el.querySelectorAll('button')).find(b => b.textContent?.includes('Reintentar')) as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.componentInstance.estadoVista).toBe('listo');
    expect(el.textContent).toContain('41,840.00');
  });

  it('usa un mensaje por omision si el servidor no envia uno', () => {
    inventario.valorizacion.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const c = crear();
    expect(c.mensajeError).toContain('valorizacion');
  });

  it('filtra el detalle a lo que necesita reposicion', () => {
    const c = crear();
    expect(c.detalle.length).toBe(2);
    c.soloReposicionEnDetalle = true;
    expect(c.detalle.map(p => p.sku)).toEqual(['RUE-1']);
  });

  it('el detalle esta vacio mientras no hay valorizacion', () => {
    const c = crear();
    c.valorizacion = undefined;
    expect(c.detalle).toEqual([]);
  });

  it('suma el valor de lo que hay que reponer', () => {
    const c = crear();
    expect(c.valorPorReponer).toBe(1840);
  });

  it('CP-29: carga la valorizacion al corte elegido', () => {
    const c = crear();
    c.fechaCorte = '2026-09-30';
    c.cargar();
    expect(inventario.valorizacion).toHaveBeenCalledWith('2026-09-30');
  });

  it('CP-29: no pide una fecha de corte futura', () => {
    const c = crear();
    inventario.valorizacion.calls.reset();
    c.fechaCorte = '2999-01-01';
    c.cargar();
    expect(inventario.valorizacion).not.toHaveBeenCalled();
    expect(c.mensajeError).toContain('posterior a hoy');
  });

  it('CP-29: volver a hoy quita el corte', () => {
    const c = crear();
    c.fechaCorte = '2026-09-30';
    c.verHoy();
    expect(c.fechaCorte).toBe('');
    expect(inventario.valorizacion).toHaveBeenCalledWith(null);
  });

  it('CP-27: exporta la valorizacion con el mismo corte', () => {
    const reportes = TestBed.inject(ReporteService);
    spyOn(reportes, 'descargar').and.returnValue(of('reporte-valorizacion.pdf'));
    const c = crear();
    c.valorizacion = { ...valorizacion, fechaCorte: '2026-09-30' };
    c.exportar('pdf');
    expect(reportes.descargar).toHaveBeenCalledWith('valorizacion', 'pdf', { fecha: '2026-09-30' });
    expect(c.mensajeDescarga).toContain('reporte-valorizacion.pdf');
  });
});

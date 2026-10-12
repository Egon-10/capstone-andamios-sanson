import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { DashboardComponent } from './dashboard';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { PanelIndicadoresComponent } from '../../components/panel-indicadores/panel-indicadores';
import { Dashboard } from '../../models/dashboard';
import { MovimientoDetalle } from '../../models/movimiento-detalle';

/** HU-39 y HU-40: pantalla de inicio con sus estados de carga, error y vacío. */
describe('DashboardComponent', () => {
  let servicio: jasmine.SpyObj<DashboardService>;
  let fixture: ComponentFixture<DashboardComponent>;

  const resumen: Dashboard = {
    totalProductos: 12, totalCategorias: 4, totalProveedores: 3, totalUsuarios: 5, stockTotal: 1530
  };

  const movimientos: MovimientoDetalle[] = [
    { id: 1, tipo: 'ENTRADA', producto: 'Marco 1.5 m', cantidad: 40, usuario: 'Ana', fecha: '2026-10-06T09:00:00' },
    { id: 2, tipo: 'SALIDA', producto: 'Tablón', cantidad: 10, usuario: 'Luis', fecha: '2026-10-07T15:30:00' },
    { id: 3, tipo: 'SALIDA', producto: 'Rueda', cantidad: 4, usuario: 'Ana', fecha: '2026-10-08T11:00:00' }
  ];

  function crear(): HTMLElement {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function filasTabla(el: HTMLElement): number {
    return el.querySelectorAll('table.ui-tabla tbody tr').length;
  }

  beforeEach(() => {
    servicio = jasmine.createSpyObj('DashboardService', [
      'obtenerResumen', 'obtenerUltimosMovimientos', 'obtenerProductosPorCategoria',
      'obtenerResumenMovimientos', 'obtenerProductosMayorStock', 'obtenerStockCritico', 'obtenerMovimientosSemana'
    ]);
    servicio.obtenerResumen.and.returnValue(of(resumen));
    servicio.obtenerUltimosMovimientos.and.returnValue(of(movimientos));
    servicio.obtenerProductosPorCategoria.and.returnValue(of([['Andamios', 7], ['Ruedas', 5]]));
    servicio.obtenerResumenMovimientos.and.returnValue(of([['ENTRADA', 1], ['SALIDA', 2]]));
    servicio.obtenerProductosMayorStock.and.returnValue(of([['Marco', 500], ['Tablón', 300]]));
    servicio.obtenerStockCritico.and.returnValue(of([['Rueda', 3, 10]]));
    servicio.obtenerMovimientosSemana.and.returnValue(of([[2, 'ENTRADA', 1], [1, 'SALIDA', 2]]));

    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: DashboardService, useValue: servicio },
        { provide: AuthService, useValue: { usuario: { nombre: 'Ana', correo: 'ana@x.pe' } } }
      ]
    });
    // El panel de indicadores tiene sus propias pruebas y su propio servicio.
    TestBed.overrideComponent(DashboardComponent, {
      remove: { imports: [PanelIndicadoresComponent] },
      add: { schemas: [CUSTOM_ELEMENTS_SCHEMA] }
    });
  });

  it('muestra la bienvenida, el resumen y los últimos movimientos', () => {
    const el = crear();
    const c = fixture.componentInstance;

    expect(el.querySelector('h1')?.textContent).toContain('Bienvenido Ana');
    expect(c.estadoResumen).toBe('listo');
    const tarjetas = el.querySelectorAll('.resumen li');
    expect(tarjetas.length).toBe(5);
    expect(el.querySelector('.resumen')?.textContent).toContain('Productos');
    expect(el.querySelector('.resumen')?.textContent).toContain('1,530');
    expect(filasTabla(el)).toBe(3);
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  it('crea las gráficas sin esperar un temporizador y las destruye al salir', () => {
    crear();
    const graficas = (fixture.componentInstance as unknown as { graficas: Map<string, { destroy(): void }> }).graficas;
    expect(graficas.size).toBe(5);
    const espias = [...graficas.values()].map(g => spyOn(g, 'destroy').and.callThrough());

    fixture.destroy();

    espias.forEach(e => expect(e).toHaveBeenCalled());
    expect(graficas.size).toBe(0);
  });

  it('cuenta el domingo en la semana (DAYOFWEEK = 1)', () => {
    crear();
    const graficas = (fixture.componentInstance as unknown as { graficas: Map<string, { data: { datasets: { data: number[] }[] } }> }).graficas;
    const [entradas, salidas] = graficas.get('semana')!.data.datasets;
    expect(entradas.data[0]).toBe(1);
    expect(salidas.data[6]).toBe(2);
  });

  it('muestra el error con reintentar cuando falla el resumen', () => {
    servicio.obtenerResumen.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const el = crear();
    const c = fixture.componentInstance;

    expect(c.estadoResumen).toBe('error');
    expect(c.resumen).toBeNull();
    expect(el.querySelector('.resumen')).toBeNull();
    const alerta = el.querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('No se pudo cargar el resumen.');

    servicio.obtenerResumen.and.returnValue(of(resumen));
    (alerta?.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(servicio.obtenerResumen).toHaveBeenCalledTimes(2);
    expect(c.estadoResumen).toBe('listo');
    expect(el.querySelectorAll('.resumen li').length).toBe(5);
  });

  it('muestra el error de la tabla sin confundirlo con una lista vacía', () => {
    servicio.obtenerUltimosMovimientos.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const el = crear();

    expect(fixture.componentInstance.estadoTabla).toBe('error');
    expect(el.textContent).toContain('No se pudieron cargar los movimientos.');
    expect(el.textContent).not.toContain('No existen movimientos registrados.');
    expect(el.querySelector('table.ui-tabla')).toBeNull();
  });

  it('filtra los movimientos por tipo y por fecha', () => {
    const el = crear();
    const c = fixture.componentInstance;

    c.filtro.tipo = 'SALIDA';
    c.aplicarFiltros();
    fixture.detectChanges();
    expect(filasTabla(el)).toBe(2);
    expect(c.movimientosFiltrados.every(m => m.tipo === 'SALIDA')).toBeTrue();

    c.filtro.desde = '2026-10-08';
    c.aplicarFiltros();
    fixture.detectChanges();
    expect(filasTabla(el)).toBe(1);
    expect(c.movimientosFiltrados[0].id).toBe(3);

    c.filtro.tipo = 'ENTRADA';
    c.aplicarFiltros();
    fixture.detectChanges();
    expect(c.estadoTabla).toBe('vacio');
    expect(el.textContent).toContain('Ningún movimiento coincide con los filtros.');

    c.limpiarFiltros();
    fixture.detectChanges();
    expect(filasTabla(el)).toBe(3);
  });

  it('muestra el estado vacío cuando no hay movimientos', () => {
    servicio.obtenerUltimosMovimientos.and.returnValue(of([]));
    const el = crear();
    expect(fixture.componentInstance.estadoTabla).toBe('vacio');
    expect(el.textContent).toContain('No existen movimientos registrados.');
  });
});

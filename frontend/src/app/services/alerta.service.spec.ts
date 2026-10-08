import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AlertaService } from './alerta.service';
import { IndicadoresService } from './indicadores.service';
import { StockCritico } from '../models/indicadores';

/** CP-30: alertas de reposición en la interfaz (HU-29). */
describe('AlertaService', () => {
  let indicadores: jasmine.SpyObj<IndicadoresService>;
  let servicio: AlertaService;

  const rueda: StockCritico = { productoId: 5, producto: 'Rueda', categoria: 'Ruedas', stock: 35, umbral: 40, faltanteHastaUmbral: 5, nivel: 'BAJO' };
  const marco: StockCritico = { productoId: 1, producto: 'Marco', categoria: 'Andamios', stock: 0, umbral: 100, faltanteHastaUmbral: 100, nivel: 'AGOTADO' };

  beforeEach(() => {
    sessionStorage.clear();
    indicadores = jasmine.createSpyObj('IndicadoresService', ['stockCritico']);
    indicadores.stockCritico.and.returnValue(of([rueda]));
    TestBed.configureTestingModule({ providers: [{ provide: IndicadoresService, useValue: indicadores }] });
    servicio = TestBed.inject(AlertaService);
  });

  afterEach(() => {
    servicio.detener();
    sessionStorage.clear();
  });

  it('CP-30: consulta al iniciar y luego cada minuto', fakeAsync(() => {
    servicio.iniciar();
    expect(indicadores.stockCritico).toHaveBeenCalledTimes(1);
    expect(servicio.cantidad()).toBe(1);
    tick(AlertaService.INTERVALO_MS);
    expect(indicadores.stockCritico).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));

  it('CP-30: iniciar dos veces no duplica la consulta periódica', fakeAsync(() => {
    servicio.iniciar();
    servicio.iniciar();
    tick(AlertaService.INTERVALO_MS);
    expect(indicadores.stockCritico).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));

  it('CP-30: una alerta es nueva hasta que el usuario la ve', () => {
    servicio.actualizar();
    expect(servicio.nuevas().length).toBe(1);
    servicio.marcarVistas();
    expect(servicio.nuevas().length).toBe(0);
  });

  it('CP-30: vuelve a ser nueva si el producto empeora de nivel', () => {
    servicio.actualizar();
    servicio.marcarVistas();
    indicadores.stockCritico.and.returnValue(of([{ ...rueda, stock: 0, nivel: 'AGOTADO' }]));
    servicio.actualizar();
    expect(servicio.nuevas().length).toBe(1);
  });

  it('CP-30: anuncia a los lectores de pantalla la alerta que aparece', () => {
    servicio.actualizar();
    expect(servicio.anuncio()).toContain('Rueda');
    indicadores.stockCritico.and.returnValue(of([rueda, marco, { ...marco, productoId: 2, producto: 'Plataforma' }]));
    servicio.actualizar();
    expect(servicio.anuncio()).toContain('2 productos nuevos');
  });

  it('CP-30: un error de red conserva las alertas que ya se mostraban', () => {
    servicio.actualizar();
    indicadores.stockCritico.and.returnValue(throwError(() => new Error('sin red')));
    servicio.actualizar();
    expect(servicio.cantidad()).toBe(1);
  });

  it('recuerda lo visto durante la sesión del navegador', () => {
    servicio.actualizar();
    servicio.marcarVistas();
    expect(JSON.parse(sessionStorage.getItem('alertasVistas')!)).toEqual({ 5: 'BAJO' });
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { PanelIndicadoresComponent } from './panel-indicadores';
import { IndicadoresService } from '../../services/indicadores.service';
import { Indicadores, StockCritico, Tendencia } from '../../models/indicadores';

/** CP-24 a CP-26: panel de indicadores de gestión (HU-23 a HU-25). */
describe('PanelIndicadoresComponent', () => {
  let fixture: ComponentFixture<PanelIndicadoresComponent>;
  let c: PanelIndicadoresComponent;
  let servicio: jasmine.SpyObj<IndicadoresService>;

  const indicadores: Indicadores = {
    productosActivos: 5, unidadesEnStock: 1130, valorInventario: 365620,
    productosPorReponer: 1, productosSinStock: 0, movimientosHoy: 3,
    entradasHoy: 1, salidasHoy: 2, ajustesPendientes: 2, fechaCorte: '2026-10-07T22:00:00'
  };

  const tendencia: Tendencia = {
    desde: '2026-06-25', hasta: '2026-06-27', totalEntradas: 3, totalSalidas: 4,
    unidadesEntrada: 70, unidadesSalida: 300,
    dias: [
      { fecha: '2026-06-25', entradas: 2, salidas: 0, unidadesEntrada: 40, unidadesSalida: 0 },
      { fecha: '2026-06-26', entradas: 0, salidas: 0, unidadesEntrada: 0, unidadesSalida: 0 },
      { fecha: '2026-06-27', entradas: 1, salidas: 4, unidadesEntrada: 30, unidadesSalida: 300 }
    ]
  };

  const critico: StockCritico[] = [
    { productoId: 2, producto: 'Marco', categoria: 'Andamios', stock: 0, umbral: 100,
      faltanteHastaUmbral: 100, reponerHastaMaximo: 500, nivel: 'AGOTADO' },
    { productoId: 5, producto: 'Rueda', categoria: 'Ruedas', stock: 35, umbral: 40,
      faltanteHastaUmbral: 5, reponerHastaMaximo: null, nivel: 'BAJO' }
  ];

  function crear(rol: string): void {
    localStorage.setItem('usuario', JSON.stringify({ id: 1, rol: { nombre: rol } }));
    fixture = TestBed.createComponent(PanelIndicadoresComponent);
    c = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    localStorage.clear();
    servicio = jasmine.createSpyObj('IndicadoresService', ['resumen', 'tendencia', 'stockCritico']);
    servicio.resumen.and.returnValue(of(indicadores));
    servicio.tendencia.and.returnValue(of(tendencia));
    servicio.stockCritico.and.returnValue(of(critico));
    TestBed.configureTestingModule({
      imports: [PanelIndicadoresComponent],
      providers: [{ provide: IndicadoresService, useValue: servicio }]
    });
  });

  afterEach(() => localStorage.clear());

  it('HU-23: el gerente ve el resumen con el valor del inventario', () => {
    crear('GERENTE');
    expect(servicio.resumen).toHaveBeenCalled();
    expect(c.indicadores?.valorInventario).toBe(365620);
    expect(fixture.nativeElement.textContent).toContain('Valor del inventario');
  });

  it('HU-23: el encargado no pide el resumen, porque incluye informacion economica', () => {
    crear('ENCARGADO');
    expect(servicio.resumen).not.toHaveBeenCalled();
    expect(c.verResumen).toBeFalse();
    expect(fixture.nativeElement.textContent).not.toContain('Valor del inventario');
    // La tendencia y el stock critico si los ve.
    expect(servicio.tendencia).toHaveBeenCalled();
    expect(c.stockCritico.length).toBe(2);
  });

  it('tolera un usuario guardado que no es JSON valido', () => {
    localStorage.setItem('usuario', '{roto');
    fixture = TestBed.createComponent(PanelIndicadoresComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.rol).toBe('');
  });

  it('muestra el error del servidor si el resumen falla', () => {
    servicio.resumen.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 500, error: { mensaje: 'Sin conexion' } })));
    crear('ADMINISTRADOR');
    expect(c.error).toBe('Sin conexion');
  });

  it('usa un mensaje por omision si el stock critico falla sin detalle', () => {
    servicio.stockCritico.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    crear('GERENTE');
    expect(c.error).toBe('No se pudo cargar el stock crítico');
  });

  it('HU-24: pide treinta dias por omision y otro periodo al cambiarlo', () => {
    crear('GERENTE');
    const [desde, hasta] = servicio.tendencia.calls.mostRecent().args;
    const dias = (new Date(hasta!).getTime() - new Date(desde!).getTime()) / 86400000;
    expect(dias).toBe(29);

    c.cambiarPeriodo(7);
    const [d7, h7] = servicio.tendencia.calls.mostRecent().args;
    expect((new Date(h7!).getTime() - new Date(d7!).getTime()) / 86400000).toBe(6);
  });

  it('HU-24: informa si la tendencia no se pudo cargar', () => {
    servicio.tendencia.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 422, error: { mensaje: 'El periodo admite hasta 92 dias' } })));
    crear('GERENTE');
    expect(c.errorTendencia).toBe('El periodo admite hasta 92 dias');
  });

  it('HU-24: dibuja un grupo por dia y omite las barras en cero', () => {
    crear('GERENTE');
    expect(c.grupos.length).toBe(3);
    expect(c.grupos[1].entrada.alto).toBe(0);
    expect(c.grupos[1].entrada.trazo).toBe('');
    expect(c.grupos[2].salida.alto).toBeGreaterThan(c.grupos[2].entrada.alto);
    // Las barras de un mismo dia no se tocan: hay un hueco de 2 px.
    const g = c.grupos[0];
    expect(g.salida.x - (g.entrada.x + g.entrada.ancho)).toBeCloseTo(2, 5);
  });

  it('HU-24: el eje usa un tope redondo y cambia con la medida', () => {
    crear('GERENTE');
    expect(c.marcasY[c.marcasY.length - 1].valor).toBe(4);
    c.cambiarMedida('unidades');
    expect(c.marcasY[c.marcasY.length - 1].valor).toBe(500);
    expect(c.valorSalida(tendencia.dias[2])).toBe(300);
    c.cambiarMedida('movimientos');
    expect(c.valorSalida(tendencia.dias[2])).toBe(4);
  });

  it('HU-24: calcula topes redondos', () => {
    crear('GERENTE');
    expect(c.topeRedondo(3)).toBe(4);
    expect(c.topeRedondo(7)).toBe(10);
    expect(c.topeRedondo(17)).toBe(20);
    expect(c.topeRedondo(23)).toBe(25);
    expect(c.topeRedondo(42)).toBe(50);
    expect(c.topeRedondo(99)).toBe(100);
  });

  it('HU-24: el tooltip aparece con el mouse y con el foco del teclado', () => {
    crear('GERENTE');
    expect(c.grupoActivo).toBeUndefined();
    expect(c.tooltipIzquierda).toBe(0);
    c.activar(2);
    expect(c.grupoActivo?.dia.fecha).toBe('2026-06-27');
    expect(c.tooltipIzquierda).toBeGreaterThan(50);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.tooltip')).not.toBeNull();
    c.desactivar();
    expect(c.grupoActivo).toBeUndefined();
  });

  it('HU-24: avisa cuando no hubo movimientos en el periodo', () => {
    servicio.tendencia.and.returnValue(of({ ...tendencia,
      dias: tendencia.dias.map(d => ({ ...d, entradas: 0, salidas: 0 })) }));
    crear('GERENTE');
    expect(c.sinMovimientos).toBeTrue();
  });

  it('HU-24: la tabla muestra los mismos datos sin depender del puntero', () => {
    crear('GERENTE');
    c.verTabla = true;
    fixture.detectChanges();
    const filas = fixture.nativeElement.querySelectorAll('.tabla-tendencia tbody tr');
    expect(filas.length).toBe(3);
  });

  it('HU-24: etiqueta los dias como dd/MM', () => {
    crear('GERENTE');
    expect(c.etiquetaDia('2026-06-27')).toBe('27/06');
  });

  it('HU-25: cada nivel lleva icono y etiqueta, no solo color', () => {
    crear('GERENTE');
    expect(c.icono('AGOTADO')).toBe('fa-circle-xmark');
    expect(c.icono('CRITICO')).toBe('fa-triangle-exclamation');
    expect(c.icono('BAJO')).toBe('fa-circle-exclamation');
    expect(c.etiquetaNivel('AGOTADO')).toBe('Agotado');
    expect(c.etiquetaNivel('CRITICO')).toBe('Crítico');
    expect(c.etiquetaNivel('BAJO')).toBe('Bajo');
    const texto = fixture.nativeElement.querySelector('.tabla-critico').textContent;
    expect(texto).toContain('Agotado');
    expect(texto).toContain('Marco');
  });

  it('HU-25: informa cuando ningun producto alcanzo su umbral', () => {
    servicio.stockCritico.and.returnValue(of([]));
    crear('GERENTE');
    expect(fixture.nativeElement.textContent).toContain('Ningún producto alcanzó su umbral');
  });
});

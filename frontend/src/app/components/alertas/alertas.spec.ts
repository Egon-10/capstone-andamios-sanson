import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal, computed } from '@angular/core';

import { AlertasComponent } from './alertas';
import { AlertaService } from '../../services/alerta.service';
import { StockCritico } from '../../models/indicadores';

/** CP-30: campana de alertas de reposición (HU-29). */
describe('AlertasComponent', () => {
  const lista = signal<StockCritico[]>([]);
  const nuevas = signal<StockCritico[]>([]);
  const servicio = {
    alertas: lista,
    nuevas,
    cantidad: computed(() => lista().length),
    anuncio: signal(''),
    esNueva: (a: StockCritico) => nuevas().includes(a),
    marcarVistas: jasmine.createSpy('marcarVistas')
  };
  const rueda: StockCritico = { productoId: 5, producto: 'Rueda', categoria: 'Ruedas', stock: 35, umbral: 40, faltanteHastaUmbral: 5, nivel: 'BAJO' };

  function crear() {
    const fixture = TestBed.createComponent(AlertasComponent);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    lista.set([]);
    nuevas.set([]);
    servicio.marcarVistas.calls.reset();
    TestBed.configureTestingModule({
      imports: [AlertasComponent],
      providers: [provideRouter([]), { provide: AlertaService, useValue: servicio }]
    });
  });

  it('CP-30: muestra el contador y describe las alertas sin revisar', () => {
    lista.set([rueda]);
    nuevas.set([rueda]);
    const f = crear();
    const boton: HTMLButtonElement = f.nativeElement.querySelector('.campana');
    expect(f.nativeElement.querySelector('.contador').textContent).toContain('1');
    expect(boton.getAttribute('aria-label')).toBe('Alertas de reposición: 1, 1 sin revisar');
    expect(f.nativeElement.querySelector('.punto')).not.toBeNull();
  });

  it('CP-30: abre la lista con nivel en texto e icono y la marca vista al cerrar', () => {
    lista.set([rueda]);
    const f = crear();
    f.componentInstance.alternar();
    f.detectChanges();
    expect(f.nativeElement.querySelector('.panel').textContent).toContain('Bajo');
    expect(f.nativeElement.querySelector('.panel').textContent).toContain('Rueda');
    f.componentInstance.alternar();
    expect(servicio.marcarVistas).toHaveBeenCalled();
  });

  it('CP-30: sin alertas lo dice explícitamente', () => {
    const f = crear();
    f.componentInstance.alternar();
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Ningún producto alcanzó su punto de reposición');
    expect(f.componentInstance.textoBoton()).toBe('Alertas de reposición: ninguna');
  });

  it('se cierra con Escape y con un clic fuera', () => {
    const f = crear();
    f.componentInstance.abierto = true;
    f.componentInstance.alPresionarEscape();
    expect(f.componentInstance.abierto).toBeFalse();
    f.componentInstance.abierto = true;
    f.componentInstance.alHacerClicFuera({ target: document.body } as unknown as Event);
    expect(f.componentInstance.abierto).toBeFalse();
  });

  it('asigna icono y etiqueta a cada nivel', () => {
    const c = crear().componentInstance;
    expect(c.icono('AGOTADO')).toBe('fa-circle-xmark');
    expect(c.icono('CRITICO')).toBe('fa-triangle-exclamation');
    expect(c.etiqueta('CRITICO')).toBe('Crítico');
  });
});

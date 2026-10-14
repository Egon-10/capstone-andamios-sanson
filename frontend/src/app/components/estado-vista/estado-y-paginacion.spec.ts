import { TestBed } from '@angular/core/testing';

import { EstadoVistaComponent } from './estado-vista';
import { PaginacionComponent } from '../paginacion/paginacion';

/** CP-41: estados de carga, vacío y error comunes (HU-40), y paginación. */
describe('EstadoVistaComponent', () => {
  function crear(estado: 'cargando' | 'error' | 'vacio' | 'listo') {
    TestBed.configureTestingModule({ imports: [EstadoVistaComponent] });
    const f = TestBed.createComponent(EstadoVistaComponent);
    f.componentInstance.estado = estado;
    f.componentInstance.detalle = 'Detalle';
    f.detectChanges();
    return f;
  }

  it('anuncia la carga como estado', () => {
    const f = crear('cargando');
    expect(f.nativeElement.querySelector('[role="status"]').textContent).toContain('Cargando');
  });

  it('muestra el error como alerta y permite reintentar', () => {
    const f = crear('error');
    const reintentar = spyOn(f.componentInstance.reintentar, 'emit');
    expect(f.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    f.nativeElement.querySelector('button').click();
    expect(reintentar).toHaveBeenCalled();
  });

  it('distingue el vacío del error', () => {
    const f = crear('vacio');
    expect(f.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(f.nativeElement.textContent).toContain('No hay registros');
  });

  it('no muestra nada cuando la vista está lista', () => {
    expect(crear('listo').nativeElement.textContent.trim()).toBe('');
  });
});

describe('PaginacionComponent', () => {
  it('calcula el rango visible y emite los cambios', () => {
    TestBed.configureTestingModule({ imports: [PaginacionComponent] });
    const f = TestBed.createComponent(PaginacionComponent);
    const c = f.componentInstance;
    c.pagina = { contenido: [1, 2, 3], pagina: 1, tamano: 10, totalElementos: 13, totalPaginas: 2, primera: false, ultima: true };
    f.detectChanges();
    expect(c.desde()).toBe(11);
    expect(c.hasta()).toBe(13);
    const tamano = spyOn(c.cambiarTamanoPagina, 'emit');
    c.cambiarTamano('50');
    expect(tamano).toHaveBeenCalledWith(50);
    expect(f.nativeElement.textContent).toContain('Página 2 de 2');
  });
});

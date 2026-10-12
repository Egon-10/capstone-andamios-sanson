import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { CategoriasComponent } from './categorias';
import { CategoriaService } from '../../services/categoria.service';

/** HU-40: estados de carga, vacío y error del listado de categorías. */
describe('CategoriasComponent', () => {
  let fixture: ComponentFixture<CategoriasComponent>;
  let c: CategoriasComponent;
  let servicio: jasmine.SpyObj<CategoriaService>;

  function crear(): void {
    fixture = TestBed.createComponent(CategoriasComponent);
    c = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    servicio = jasmine.createSpyObj('CategoriaService', ['listar', 'crear', 'actualizar', 'eliminar']);
    TestBed.configureTestingModule({
      imports: [CategoriasComponent],
      providers: [{ provide: CategoriaService, useValue: servicio }]
    });
  });

  it('muestra las categorias que devuelve el servidor', () => {
    servicio.listar.and.returnValue(of([{ id: 1, nombre: 'Andamios' }]));
    crear();
    expect(c.estadoLista).toBe('listo');
    expect(fixture.nativeElement.querySelector('table').textContent).toContain('Andamios');
  });

  it('HU-40: sin categorias explica que aun no hay registros', () => {
    servicio.listar.and.returnValue(of([]));
    crear();
    expect(c.estadoLista).toBe('vacio');
    expect(fixture.nativeElement.textContent).toContain('Aún no hay categorías registradas.');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('HU-40: si falla la consulta muestra el error y reintentar vuelve a consultar', () => {
    servicio.listar.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 500, error: { mensaje: 'Servidor no disponible' } })));
    crear();

    expect(c.estadoLista).toBe('error');
    const alerta: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alerta.textContent).toContain('Servidor no disponible');

    servicio.listar.and.returnValue(of([{ id: 1, nombre: 'Andamios' }]));
    alerta.querySelector('button')!.click();
    fixture.detectChanges();

    expect(servicio.listar).toHaveBeenCalledTimes(2);
    expect(c.estadoLista).toBe('listo');
  });
});

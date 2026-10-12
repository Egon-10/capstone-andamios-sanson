import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { ProveedoresComponent } from './proveedores';
import { ProveedorService } from '../../services/proveedor.service';
import { Proveedor } from '../../models/proveedor';

/** HU-40: estados de carga, vacío y error del listado de proveedores. */
describe('ProveedoresComponent', () => {
  let fixture: ComponentFixture<ProveedoresComponent>;
  let c: ProveedoresComponent;
  let servicio: jasmine.SpyObj<ProveedorService>;

  const proveedor: Proveedor = {
    id: 1, nombre: 'Aceros del Norte', ruc: '20123456789',
    direccion: 'Av. Industrial 123', telefono: '987654321', correo: 'ventas@aceros.pe'
  };

  function crear(): void {
    fixture = TestBed.createComponent(ProveedoresComponent);
    c = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    servicio = jasmine.createSpyObj('ProveedorService', ['listar', 'crear', 'actualizar', 'eliminar']);
    TestBed.configureTestingModule({
      imports: [ProveedoresComponent],
      providers: [{ provide: ProveedorService, useValue: servicio }]
    });
  });

  it('muestra los proveedores que devuelve el servidor', () => {
    servicio.listar.and.returnValue(of([proveedor]));
    crear();
    expect(c.estadoLista).toBe('listo');
    expect(fixture.nativeElement.querySelector('table').textContent).toContain('Aceros del Norte');
  });

  it('HU-40: sin proveedores explica que aun no hay registros', () => {
    servicio.listar.and.returnValue(of([]));
    crear();
    expect(c.estadoLista).toBe('vacio');
    expect(fixture.nativeElement.textContent).toContain('Aún no hay proveedores registrados.');
  });

  it('HU-40: si falla la consulta muestra el error y reintentar vuelve a consultar', () => {
    servicio.listar.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    crear();

    expect(c.estadoLista).toBe('error');
    const alerta: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alerta.textContent).toContain('No se pudieron cargar los proveedores.');

    servicio.listar.and.returnValue(of([proveedor]));
    alerta.querySelector('button')!.click();
    fixture.detectChanges();

    expect(servicio.listar).toHaveBeenCalledTimes(2);
    expect(c.estadoLista).toBe('listo');
  });

  it('valida el RUC antes de llamar al servidor', () => {
    servicio.listar.and.returnValue(of([]));
    crear();
    c.proveedor = { ...proveedor, id: undefined, ruc: '123' };
    c.guardar();
    expect(c.mensajeError).toBe('El RUC debe tener exactamente 11 dígitos.');
    expect(servicio.crear).not.toHaveBeenCalled();
  });
});

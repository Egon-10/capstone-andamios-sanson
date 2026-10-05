import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AjusteService } from './ajuste.service';
import { environment } from '../../environments/environment';

/**
 * CP-17: ajuste de inventario por conteo físico.
 *
 * Lo que fijan estas pruebas es la separación entre registrar y aprobar: son
 * dos llamadas distintas, y la de registro no envía el stock de sistema, que
 * lo lee el servidor. Si el cliente lo enviara, podría declarar una diferencia
 * distinta de la real.
 */
describe('AjusteService', () => {
  let service: AjusteService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/ajustes`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AjusteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('registra el conteo sin enviar el stock de sistema ni el usuario', () => {
    service.registrar(5, 95, 'Faltan cinco en el estante').subscribe();

    const peticion = http.expectOne(api);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({
      productoId: 5,
      stockFisico: 95,
      observacion: 'Faltan cinco en el estante'
    });

    const cuerpo = JSON.stringify(peticion.request.body);
    expect(cuerpo).not.toContain('stockSistema');
    expect(cuerpo).not.toContain('diferencia');
    expect(cuerpo).not.toContain('usuarioId');

    peticion.flush({ id: 1, estado: 'PENDIENTE' });
  });

  it('aprueba con una llamada propia, separada del registro (HU-16)', () => {
    service.aprobar(42).subscribe();

    const peticion = http.expectOne(`${api}/42/aprobacion`);

    expect(peticion.request.method).toBe('POST');
    peticion.flush({ id: 42, estado: 'APROBADO', movimientoId: 200 });
  });

  it('rechaza enviando el motivo', () => {
    service.rechazar(42, 'El conteo no incluyo el deposito dos').subscribe();

    const peticion = http.expectOne(`${api}/42/rechazo`);

    expect(peticion.request.body).toEqual({
      motivo: 'El conteo no incluyo el deposito dos'
    });
    peticion.flush({ id: 42, estado: 'RECHAZADO' });
  });

  it('omite del filtro los parametros que no se informaron', () => {
    service.listar('PENDIENTE').subscribe();

    const peticion = http.expectOne(r => r.url === api);

    expect(peticion.request.params.get('estado')).toBe('PENDIENTE');
    expect(peticion.request.params.has('productoId')).toBeFalse();
    peticion.flush([]);
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { MovimientoService } from './movimiento.service';
import { environment } from '../../environments/environment';

/**
 * CP-13 y CP-16: el cliente no decide a nombre de quién se registra un
 * movimiento, y un movimiento no se edita ni se borra.
 *
 * La primera prueba es la que fija la corrección de autorización: la versión
 * anterior enviaba usuarioId en la URL, de modo que cualquier usuario con
 * sesión válida podía atribuir un movimiento a otra persona. Si alguien
 * reintroduce ese parámetro, esta prueba falla.
 */
describe('MovimientoService', () => {
  let service: MovimientoService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/movimientos`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(MovimientoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('registra una entrada sin enviar el identificador del usuario (CR-04)', () => {
    service.registrarEntrada({
      productoId: 7,
      cantidad: 5,
      motivo: 'COMPRA',
      costoUnitario: 310.5
    }).subscribe();

    const peticion = http.expectOne(`${api}/entrada`);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.urlWithParams).toBe(`${api}/entrada`);
    expect(peticion.request.body).toEqual({
      productoId: 7,
      cantidad: 5,
      motivo: 'COMPRA',
      costoUnitario: 310.5
    });

    // La identidad la resuelve el servidor desde el token, no el cliente.
    expect(JSON.stringify(peticion.request.body)).not.toContain('usuarioId');
    expect(peticion.request.params.has('usuarioId')).toBeFalse();

    peticion.flush({ id: 1 });
  });

  it('registra una salida con su motivo tipificado (HU-14)', () => {
    service.registrarSalida({
      productoId: 3,
      cantidad: 2,
      motivo: 'MERMA',
      observacion: 'Dos planchas dobladas en el traslado'
    }).subscribe();

    const peticion = http.expectOne(`${api}/salida`);

    expect(peticion.request.body.motivo).toBe('MERMA');
    expect(peticion.request.body.observacion)
      .toBe('Dos planchas dobladas en el traslado');
    expect(peticion.request.params.has('usuarioId')).toBeFalse();

    peticion.flush({ id: 2 });
  });

  it('anula un movimiento con su justificación, en lugar de borrarlo (HU-15)', () => {
    service.anular(42, 'Se registró la salida en el producto equivocado').subscribe();

    const peticion = http.expectOne(`${api}/42/anulacion`);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({
      justificacion: 'Se registró la salida en el producto equivocado'
    });

    peticion.flush({ id: 43, estado: 'COMPENSACION', movimientoOrigenId: 42 });
  });

  it('no expone métodos para editar ni borrar un movimiento (HU-15)', () => {
    // Un movimiento es inmutable: el servidor deniega PUT y DELETE, y el
    // cliente no debe ofrecer una vía para intentarlo.
    const cualquiera = service as unknown as Record<string, unknown>;
    expect(cualquiera['actualizar']).toBeUndefined();
    expect(cualquiera['eliminar']).toBeUndefined();
  });

  it('omite del filtro los parámetros que no se informaron', () => {
    service.filtrar(undefined, undefined, 'SALIDA').subscribe();

    const peticion = http.expectOne(r => r.url === `${api}/filtrar`);

    expect(peticion.request.params.get('tipo')).toBe('SALIDA');
    expect(peticion.request.params.has('fechaInicio')).toBeFalse();
    expect(peticion.request.params.has('fechaFin')).toBeFalse();

    peticion.flush([]);
  });
});

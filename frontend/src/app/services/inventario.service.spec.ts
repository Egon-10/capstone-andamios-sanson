import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { InventarioService } from './inventario.service';
import { environment } from '../../environments/environment';

/** CP-18 y CP-19: valorización y kardex. */
describe('InventarioService', () => {
  let service: InventarioService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/inventario`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(InventarioService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide la valorizacion del inventario', () => {
    service.valorizacion().subscribe(v => {
      expect(v.valorTotal).toBe(365620);
    });

    const peticion = http.expectOne(`${api}/valorizacion`);
    expect(peticion.request.method).toBe('GET');
    peticion.flush({ valorTotal: 365620, porCategoria: [], detalle: [] });
  });

  it('pide el kardex de un producto sin fechas cuando no se acotan', () => {
    service.kardex(5).subscribe();

    const peticion = http.expectOne(r => r.url === `${api}/kardex/5`);

    expect(peticion.request.params.has('desde')).toBeFalse();
    expect(peticion.request.params.has('hasta')).toBeFalse();
    peticion.flush({ productoId: 5, lineas: [] });
  });

  it('acota el kardex por fechas cuando se informan', () => {
    service.kardex(5, '2026-01-01', '2026-03-31').subscribe();

    const peticion = http.expectOne(r => r.url === `${api}/kardex/5`);

    expect(peticion.request.params.get('desde')).toBe('2026-01-01');
    expect(peticion.request.params.get('hasta')).toBe('2026-03-31');
    peticion.flush({ productoId: 5, lineas: [] });
  });

  it('pide los productos por reponer', () => {
    service.porReponer().subscribe();

    const peticion = http.expectOne(`${api}/por-reponer`);
    expect(peticion.request.method).toBe('GET');
    peticion.flush([]);
  });
});

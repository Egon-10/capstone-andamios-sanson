import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { IndicadoresService } from './indicadores.service';
import { environment } from '../../environments/environment';

/** CP-24 a CP-26: llamadas del panel de indicadores. */
describe('IndicadoresService', () => {
  let service: IndicadoresService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/indicadores`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(IndicadoresService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide el resumen de indicadores', () => {
    service.resumen().subscribe(r => expect(r.productosActivos).toBe(5));
    const p = http.expectOne(`${api}/resumen`);
    expect(p.request.method).toBe('GET');
    p.flush({ productosActivos: 5 });
  });

  it('pide la tendencia sin fechas cuando no se informan', () => {
    service.tendencia().subscribe();
    const p = http.expectOne(r => r.url === `${api}/tendencia`);
    expect(p.request.params.keys().length).toBe(0);
    p.flush({ dias: [] });
  });

  it('pide la tendencia del periodo indicado', () => {
    service.tendencia('2026-09-01', '2026-09-30').subscribe();
    const p = http.expectOne(r => r.url === `${api}/tendencia`);
    expect(p.request.params.get('desde')).toBe('2026-09-01');
    expect(p.request.params.get('hasta')).toBe('2026-09-30');
    p.flush({ dias: [] });
  });

  it('pide el stock critico', () => {
    service.stockCritico().subscribe();
    http.expectOne(`${api}/stock-critico`).flush([]);
  });
});

import { TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ReporteService, nombreDeArchivo } from './reporte.service';

/** CP-27 y CP-28: descarga de reportes desde el cliente. */
describe('ReporteService', () => {
  let servicio: ReporteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ReporteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide el reporte con el formato y solo los filtros con valor, y lo guarda con el nombre del servidor', () => {
    const guardar = spyOn(servicio, 'guardar');
    let nombre = '';
    servicio.descargar('movimientos', 'xlsx', { tipo: 'ENTRADA', desde: '', hasta: null }).subscribe(n => (nombre = n));

    const req = http.expectOne(r => r.url.endsWith('/reportes/movimientos'));
    expect(req.request.params.get('formato')).toBe('xlsx');
    expect(req.request.params.get('tipo')).toBe('ENTRADA');
    expect(req.request.params.has('desde')).toBeFalse();
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x']), {
      headers: { 'Content-Disposition': "attachment; filename=\"reporte.xlsx\"; filename*=UTF-8''reporte-movimientos-20261008.xlsx" }
    });

    expect(nombre).toBe('reporte-movimientos-20261008.xlsx');
    expect(guardar).toHaveBeenCalledWith(jasmine.any(Blob), 'reporte-movimientos-20261008.xlsx');
  });

  it('el kardex lleva el producto en la ruta', () => {
    spyOn(servicio, 'guardar');
    servicio.descargarKardex(7, 'pdf').subscribe();
    http.expectOne(r => r.url.endsWith('/reportes/kardex/7')).flush(new Blob(['x']));
  });

  it('lee el nombre de archivo de Content-Disposition, con y sin tildes', () => {
    const conTilde = new HttpResponse({ headers: new HttpHeaders({ 'Content-Disposition': "attachment; filename*=UTF-8''valorizaci%C3%B3n.pdf" }) });
    const simple = new HttpResponse({ headers: new HttpHeaders({ 'Content-Disposition': 'attachment; filename="a.pdf"' }) });
    const sinCabecera = new HttpResponse({});
    expect(nombreDeArchivo(conTilde, 'x')).toBe('valorización.pdf');
    expect(nombreDeArchivo(simple, 'x')).toBe('a.pdf');
    expect(nombreDeArchivo(sinCabecera, 'reporte.pdf')).toBe('reporte.pdf');
  });

  it('guardar crea un enlace temporal y lo pulsa', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:prueba');
    const clic = spyOn(HTMLAnchorElement.prototype, 'click');
    servicio.guardar(new Blob(['x']), 'r.pdf');
    expect(clic).toHaveBeenCalled();
  });
});

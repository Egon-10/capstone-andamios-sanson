import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ProductoService } from './producto.service';
import { environment } from '../../environments/environment';

/**
 * CP-20, CP-22 y CP-23: carga masiva, búsqueda paginada y ficha.
 *
 * La prueba de la búsqueda es la que fija la HU-21: los filtros viajan como
 * parámetros al servidor y no se aplican en el navegador, que es el cambio que
 * hace que la búsqueda siga funcionando cuando el catálogo crece.
 */
describe('ProductoService (Sprint 2)', () => {
  let service: ProductoService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/productos`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ProductoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('HU-21: envia los filtros y la paginacion al servidor', () => {
    service.buscar({
      q: 'plataforma',
      categoriaId: 3,
      porReponer: true,
      pagina: 2,
      tamano: 50,
      orden: 'stock',
      direccion: 'desc'
    }).subscribe();

    const peticion = http.expectOne(r => r.url === `${api}/buscar`);
    const params = peticion.request.params;

    expect(params.get('q')).toBe('plataforma');
    expect(params.get('categoriaId')).toBe('3');
    expect(params.get('porReponer')).toBe('true');
    expect(params.get('pagina')).toBe('2');
    expect(params.get('tamano')).toBe('50');
    expect(params.get('orden')).toBe('stock');
    expect(params.get('direccion')).toBe('desc');

    peticion.flush({ contenido: [], pagina: 2, tamano: 50, totalElementos: 0,
      totalPaginas: 0, primera: true, ultima: true });
  });

  it('HU-21: aplica valores por omision cuando no se informan', () => {
    service.buscar({}).subscribe();

    const peticion = http.expectOne(r => r.url === `${api}/buscar`);
    const params = peticion.request.params;

    expect(params.get('pagina')).toBe('0');
    expect(params.get('tamano')).toBe('20');
    expect(params.get('orden')).toBe('nombre');
    expect(params.get('direccion')).toBe('asc');
    expect(params.has('q')).toBeFalse();

    peticion.flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0,
      totalPaginas: 0, primera: true, ultima: true });
  });

  it('HU-22: pide la ficha consolidada del producto', () => {
    service.ficha(7, 5).subscribe();

    const peticion = http.expectOne(r => r.url === `${api}/7/ficha`);

    expect(peticion.request.params.get('ultimosMovimientos')).toBe('5');
    peticion.flush({ id: 7, ultimosMovimientos: [] });
  });

  it('HU-20: actualiza solo los umbrales, sin reenviar el resto de la ficha', () => {
    service.actualizarUmbrales(7, { stockMinimo: 10, puntoReposicion: 15 }).subscribe();

    const peticion = http.expectOne(`${api}/7/umbrales`);

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ stockMinimo: 10, puntoReposicion: 15 });

    // El cuerpo no lleva nombre, precio ni stock: cambiarlos no es el objeto
    // de esta operacion y enviarlos vacios los perderia.
    const cuerpo = JSON.stringify(peticion.request.body);
    expect(cuerpo).not.toContain('nombre');
    expect(cuerpo).not.toContain('precio');
    expect(cuerpo).not.toContain('"stock"');

    peticion.flush({ id: 7 });
  });

  it('HU-19: envia el archivo como multipart con el modo y la simulacion', () => {
    const archivo = new File(['sku,nombre\nAND-1,Marco'], 'productos.csv',
      { type: 'text/csv' });

    service.cargaMasiva(archivo, 'PARCIAL', true).subscribe();

    const peticion = http.expectOne(r => r.url === `${api}/carga-masiva`);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body instanceof FormData).toBeTrue();
    expect((peticion.request.body as FormData).get('archivo')).toBe(archivo);
    expect(peticion.request.params.get('modo')).toBe('PARCIAL');
    expect(peticion.request.params.get('simulacion')).toBe('true');

    peticion.flush({ aceptadas: 1, rechazadas: 0, errores: [], skusCargados: ['AND-1'] });
  });
});

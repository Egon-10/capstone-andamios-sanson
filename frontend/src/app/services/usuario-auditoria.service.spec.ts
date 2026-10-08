import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { UsuarioService } from './usuario.service';
import { AuditoriaService } from './auditoria.service';

/** CP-31 a CP-35: servicios del cliente para usuarios y bitácoras. */
describe('UsuarioService y AuditoriaService (Sprint 3)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('CP-31: busca usuarios con los filtros como parámetros', () => {
    TestBed.inject(UsuarioService).buscar({ texto: 'luis', rolId: 3, estado: '', pagina: 1, tamano: 10 }).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/usuarios'));
    expect(req.request.params.get('texto')).toBe('luis');
    expect(req.request.params.get('rolId')).toBe('3');
    expect(req.request.params.has('estado')).toBeFalse();
    req.flush({ contenido: [], pagina: 1, tamano: 10, totalElementos: 0, totalPaginas: 0, primera: false, ultima: true });
  });

  it('CP-32: cambia el estado con el motivo', () => {
    TestBed.inject(UsuarioService).cambiarEstado(4, 'INACTIVO', 'Cese').subscribe();
    const req = http.expectOne(r => r.url.endsWith('/usuarios/4/estado'));
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ estado: 'INACTIVO', motivo: 'Cese' });
    req.flush({});
  });

  it('CP-35: pide el perfil propio y las opciones de usuarios', () => {
    const s = TestBed.inject(UsuarioService);
    s.miPerfil().subscribe();
    s.opciones().subscribe();
    http.expectOne(r => r.url.endsWith('/usuarios/me')).flush({ usuario: {}, fallidosDesdeAnterior: 0 });
    http.expectOne(r => r.url.endsWith('/usuarios/opciones')).flush([]);
  });

  it('CP-34 y CP-33: consulta las dos bitácoras', () => {
    const s = TestBed.inject(AuditoriaService);
    s.buscar({ texto: 'anul', pagina: 0 }).subscribe();
    s.accesos({ resultado: 'FALLIDO' }).subscribe();
    s.ultimos().subscribe();
    expect(http.expectOne(r => r.url.endsWith('/auditoria')).request.params.get('texto')).toBe('anul');
    expect(http.expectOne(r => r.url.endsWith('/accesos')).request.params.get('resultado')).toBe('FALLIDO');
    http.expectOne(r => r.url.endsWith('/auditoria/ultimos')).flush([]);
  });
});

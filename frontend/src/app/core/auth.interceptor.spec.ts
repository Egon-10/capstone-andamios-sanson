import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../environments/environment';
import { authInterceptor } from './auth.interceptor';

/** CP-06 y HU-09: el interceptor envía el token y renueva la sesión ante un 401. */
describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const api = environment.apiUrl;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('agrega el token de acceso a las peticiones a la API', () => {
    localStorage.setItem('accessToken', 'token-1');

    http.get(`${api}/productos`).subscribe();

    const req = backend.expectOne(`${api}/productos`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    req.flush([]);
  });

  it('no envía el token al iniciar sesión', () => {
    localStorage.setItem('accessToken', 'token-1');

    http.post(`${api}/auth/login`, {}).subscribe();

    const req = backend.expectOne(`${api}/auth/login`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('ante un 401 renueva la sesión una vez y reintenta con el token nuevo', () => {
    localStorage.setItem('accessToken', 'vencido');
    localStorage.setItem('refreshToken', 'renovacion-1');
    let respuesta: unknown;

    http.get(`${api}/productos`).subscribe(r => (respuesta = r));

    backend.expectOne(`${api}/productos`).flush(null, { status: 401, statusText: 'Unauthorized' });

    const renovacion = backend.expectOne(`${api}/auth/refresh`);
    expect(renovacion.request.body).toEqual({ refreshToken: 'renovacion-1' });
    renovacion.flush({
      accessToken: 'nuevo',
      refreshToken: 'renovacion-2',
      tipo: 'Bearer',
      expiraEnSegundos: 900,
      inactividadMaximaSegundos: 1800,
      usuario: { id: 1, nombre: 'Ana', correo: 'a@a.pe', rol: { id: 1, nombre: 'ADMINISTRADOR' } }
    });

    const reintento = backend.expectOne(`${api}/productos`);
    expect(reintento.request.headers.get('Authorization')).toBe('Bearer nuevo');
    reintento.flush([{ id: 1 }]);

    expect(respuesta).toEqual([{ id: 1 }]);
    expect(localStorage.getItem('refreshToken')).toBe('renovacion-2');
  });

  it('si la renovación falla, cierra la sesión local', () => {
    localStorage.setItem('accessToken', 'vencido');
    localStorage.setItem('refreshToken', 'expirado');
    localStorage.setItem('usuario', '{"id":1}');

    http.get(`${api}/productos`).subscribe({ error: () => undefined });

    backend.expectOne(`${api}/productos`).flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(`${api}/auth/refresh`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('usuario')).toBeNull();
  });
});

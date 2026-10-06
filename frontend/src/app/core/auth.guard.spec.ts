import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { authChildGuard, authGuard } from './auth.guard';

/** CP-06: el cliente no muestra rutas protegidas sin sesión ni a roles sin permiso. */
describe('authGuard (CP-06)', () => {
  let router: Router;

  const ruta = (roles?: string[]) => ({ data: roles ? { roles } : {} }) as unknown as ActivatedRouteSnapshot;

  function iniciarSesionComo(rol: string): void {
    localStorage.setItem('accessToken', 'token');
    localStorage.setItem('usuario', JSON.stringify({ id: 1, nombre: 'Ana', correo: 'a@a.pe', rol: { id: 1, nombre: rol } }));
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient()] });
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('redirige al inicio de sesión cuando no hay token', () => {
    const resultado = TestBed.runInInjectionContext(() => authGuard(ruta()));
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/login');
  });

  it('permite el acceso con sesión iniciada', () => {
    iniciarSesionComo('ENCARGADO');
    const resultado = TestBed.runInInjectionContext(() => authGuard(ruta()));
    expect(resultado).toBeTrue();
  });

  it('impide a un rol sin permiso entrar a una ruta restringida', () => {
    iniciarSesionComo('ENCARGADO');
    const resultado = TestBed.runInInjectionContext(() => authChildGuard(ruta(['ADMINISTRADOR'])));
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/dashboard');
  });

  it('permite al administrador entrar a la gestión de usuarios', () => {
    iniciarSesionComo('ADMINISTRADOR');
    const resultado = TestBed.runInInjectionContext(() => authChildGuard(ruta(['ADMINISTRADOR'])));
    expect(resultado).toBeTrue();
  });
});

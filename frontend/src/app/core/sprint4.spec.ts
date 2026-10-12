import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { authGuard, cambioObligatorioGuard, loginGuard } from './auth.guard';
import { faltas, normalizar } from './politica-contrasena';
import { AuthService } from '../services/auth.service';
import { mensajeDeLogin } from '../pages/login/login';

/** CP-36 a CP-38 en el cliente: política, contraseña temporal y mensajes de acceso. */
describe('Política de contraseñas (CP-38)', () => {
  it('acepta una contraseña que cumple todas las reglas', () => {
    expect(faltas('Clave#Segura2026', 'ana.perez', '45678912')).toEqual([]);
  });

  it('señala cada regla incumplida', () => {
    const ids = (c: string) => faltas(c, 'ana.perez', '45678912').map(r => r.id);
    expect(ids('Ab#1')).toContain('largo');
    expect(ids('clave#segura2026')).toContain('mayuscula');
    expect(ids('CLAVE#SEGURA2026')).toContain('minuscula');
    expect(ids('Clave#SeguraXYZ')).toContain('numero');
    expect(ids('ClaveSegura2026')).toContain('simbolo');
    expect(ids('Clave Segura#2026')).toContain('espacios');
    expect(ids('Ana.Perez#2026x')).toContain('datos');
    expect(ids('Doc#45678912ab')).toContain('datos');
    expect(ids('Andamios-2026!')).toContain('previsible');
    expect(ids('')).toContain('largo');
  });

  it('normaliza tildes, mayúsculas y símbolos', () => {
    expect(normalizar('Andamios-2026!')).toBe('andamios2026');
    expect(normalizar('Sansón')).toBe('sanson');
  });
});

describe('Contraseña temporal en el cliente (CP-37)', () => {
  let router: Router;
  const ruta = {} as ActivatedRouteSnapshot;
  const estado = {} as RouterStateSnapshot;

  function sesion(debeCambiar: boolean): void {
    localStorage.setItem('accessToken', 'token');
    localStorage.setItem('usuario', JSON.stringify({ id: 1, nombre: 'Ana', correo: 'a@a.pe',
      rol: { id: 1, nombre: 'GERENTE' }, debeCambiarPassword: debeCambiar }));
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('con contraseña temporal, toda ruta lleva al cambio obligatorio', () => {
    sesion(true);
    const r = TestBed.runInInjectionContext(() => authGuard({ data: {} } as unknown as ActivatedRouteSnapshot, estado));
    expect(router.serializeUrl(r as UrlTree)).toBe('/cambiar-password');
    const l = TestBed.runInInjectionContext(() => loginGuard(ruta, estado));
    expect(router.serializeUrl(l as UrlTree)).toBe('/cambiar-password');
  });

  it('la pantalla de cambio obligatorio solo se abre con contraseña temporal', () => {
    expect(router.serializeUrl(TestBed.runInInjectionContext(() => cambioObligatorioGuard(ruta, estado)) as UrlTree)).toBe('/login');
    sesion(false);
    expect(router.serializeUrl(TestBed.runInInjectionContext(() => cambioObligatorioGuard(ruta, estado)) as UrlTree)).toBe('/dashboard');
    sesion(true);
    expect(TestBed.runInInjectionContext(() => cambioObligatorioGuard(ruta, estado))).toBeTrue();
  });

  it('el cambio de contraseña guarda la sesión nueva que devuelve el servidor', () => {
    sesion(true);
    const auth = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);
    auth.cambiarPassword('Tmp#Clave2026xy', 'Nueva#Clave2026', 'Nueva#Clave2026').subscribe();
    const req = http.expectOne(r => r.url.endsWith('/auth/cambio-password'));
    expect(req.request.body).toEqual({ actual: 'Tmp#Clave2026xy', nueva: 'Nueva#Clave2026', confirmacion: 'Nueva#Clave2026' });
    req.flush({ accessToken: 'nuevo', refreshToken: 'r', tipo: 'Bearer', expiraEnSegundos: 900, inactividadMaximaSegundos: 1800,
      usuario: { id: 1, nombre: 'Ana', correo: 'a@a.pe', debeCambiarPassword: false } });
    expect(auth.accessToken).toBe('nuevo');
    expect(auth.debeCambiarPassword).toBeFalse();
  });
});

describe('Mensajes del inicio de sesión (CP-36, CP-39)', () => {
  it('no distingue entre usuario y contraseña incorrectos', () => {
    expect(mensajeDeLogin(new HttpErrorResponse({ status: 401 }))).toBe('Usuario o contraseña incorrectos.');
  });

  it('muestra el mensaje del bloqueo y del límite de intentos', () => {
    const bloqueo = new HttpErrorResponse({ status: 423, error: { mensaje: 'La cuenta está bloqueada temporalmente. Intente de nuevo en 15 minutos' } });
    expect(mensajeDeLogin(bloqueo)).toContain('15 minutos');
    expect(mensajeDeLogin(new HttpErrorResponse({ status: 429 }))).toContain('Demasiados intentos');
    expect(mensajeDeLogin(new HttpErrorResponse({ status: 0 }))).toContain('conectar');
  });
});

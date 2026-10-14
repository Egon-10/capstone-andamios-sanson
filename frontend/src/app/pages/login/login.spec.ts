import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { throwError } from 'rxjs';

import { LoginComponent } from './login';
import { AuthService } from '../../services/auth.service';

/** HU-39: inicio de sesión accesible (etiquetas, mostrar contraseña y alertas). */
describe('LoginComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let motivo: string | null = null;

  function crear() {
    const f = TestBed.createComponent(LoginComponent);
    f.detectChanges();
    return f;
  }

  beforeEach(() => {
    motivo = null;
    auth = jasmine.createSpyObj('AuthService', ['login']);
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { get queryParamMap() { return convertToParamMap(motivo ? { motivo } : {}); } } } }
      ]
    });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  it('asocia cada campo con su etiqueta', () => {
    const el: HTMLElement = crear().nativeElement;
    expect(el.querySelector('label[for="login-correo"]')).not.toBeNull();
    expect(el.querySelector('#login-correo')).not.toBeNull();
    expect(el.querySelector('label[for="login-password"]')).not.toBeNull();
    expect(el.querySelector('#login-password')).not.toBeNull();
  });

  it('muestra y oculta la contraseña con un botón con nombre accesible', () => {
    const f = crear();
    const el: HTMLElement = f.nativeElement;
    const boton = el.querySelector('button.ver') as HTMLButtonElement;
    const clave = el.querySelector('#login-password') as HTMLInputElement;

    expect(clave.type).toBe('password');
    expect(boton.getAttribute('aria-label')).toBe('Mostrar contraseña');

    boton.click();
    f.detectChanges();

    expect(clave.type).toBe('text');
    expect(boton.getAttribute('aria-label')).toBe('Ocultar contraseña');
    expect(boton.getAttribute('aria-pressed')).toBe('true');
  });

  it('anuncia el error del servidor con role="alert"', () => {
    auth.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const f = crear();
    f.componentInstance.loginData = { correo: 'ana', password: 'x' };
    f.componentInstance.iniciarSesion();
    f.detectChanges();

    const alerta = f.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alerta.textContent).toContain('Usuario o contraseña incorrectos.');
    expect(f.nativeElement.querySelector('#login-password').getAttribute('aria-invalid')).toBe('true');
  });

  it('avisa que la sesión se cerró por inactividad', () => {
    motivo = 'inactividad';
    const el: HTMLElement = crear().nativeElement;
    expect(el.querySelector('.ui-mensaje.aviso[role="alert"]')?.textContent).toContain('inactividad');
  });
});

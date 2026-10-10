import { RespuestaLogin } from '../../models/respuesta-login';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { CambiarPasswordComponent } from './cambiar-password';
import { AuthService } from '../../services/auth.service';

/** CP-37: cambio obligatorio de la contraseña temporal (HU-36). */
describe('CambiarPasswordComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;

  function crear() {
    const f = TestBed.createComponent(CambiarPasswordComponent);
    f.detectChanges();
    return f;
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['cambiarPassword', 'cerrarSesion'], {
      usuario: { id: 2, nombre: 'Luis', correo: 'l@a.pe', nombreUsuario: 'luis.torres', numeroDocumento: '45678912' }
    });
    TestBed.configureTestingModule({
      imports: [CambiarPasswordComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }]
    });
  });

  it('saluda a la persona y muestra los requisitos que se marcan al escribir', () => {
    const f = crear();
    f.componentInstance.nueva = 'Nueva#Clave2026';
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Hola Luis');
    expect(f.nativeElement.querySelectorAll('.politica li.cumple').length).toBe(8);
  });

  it('cambia la contraseña y entra al panel', () => {
    auth.cambiarPassword.and.returnValue(of({} as RespuestaLogin));
    const f = crear();
    const navegar = spyOn(TestBed.inject(Router), 'navigate');
    const c = f.componentInstance;
    c.actual = 'Tmp#Clave2026xy';
    c.nueva = c.confirmacion = 'Nueva#Clave2026';
    c.cambiar();
    expect(auth.cambiarPassword).toHaveBeenCalledWith('Tmp#Clave2026xy', 'Nueva#Clave2026', 'Nueva#Clave2026');
    expect(navegar).toHaveBeenCalledWith(['/dashboard']);
  });

  it('valida antes de enviar', () => {
    const c = crear().componentInstance;
    c.cambiar();
    expect(c.error).toContain('temporal');
    c.actual = 'Tmp#Clave2026xy';
    c.nueva = 'Luis.Torres#2026';
    c.confirmacion = c.nueva;
    c.cambiar();
    expect(c.error).toContain('requisitos');
    c.nueva = 'Nueva#Clave2026';
    c.cambiar();
    expect(c.error).toContain('no coinciden');
    c.actual = c.nueva = c.confirmacion = 'Igual#Clave2026';
    c.cambiar();
    expect(c.error).toContain('distinta');
    expect(auth.cambiarPassword).not.toHaveBeenCalled();
  });

  it('muestra el rechazo del servidor y permite cerrar sesión', () => {
    auth.cambiarPassword.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 422, error: { estado: 422, mensaje: 'x', errores: { actual: 'La contraseña actual no es correcta' } } })));
    const c = crear().componentInstance;
    c.actual = 'Mala#Clave2026';
    c.nueva = c.confirmacion = 'Nueva#Clave2026';
    c.cambiar();
    expect(c.error).toBe('La contraseña actual no es correcta');
    c.salir();
    expect(auth.cerrarSesion).toHaveBeenCalled();
  });
});

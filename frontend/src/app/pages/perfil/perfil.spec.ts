import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { PerfilComponent } from './perfil';
import { UsuarioService } from '../../services/usuario.service';
import { AuthService } from '../../services/auth.service';
import { Perfil } from '../../models/usuario';

/** CP-35: perfil propio (HU-34). */
describe('PerfilComponent', () => {
  let servicio: jasmine.SpyObj<UsuarioService>;
  let auth: jasmine.SpyObj<AuthService>;

  const perfil: Perfil = {
    usuario: { id: 2, nombre: 'Luis', apellidos: 'Rojas', correo: 'luis@a.pe', nombreUsuario: 'luis',
      area: 'LOGISTICA', rol: { id: 3, nombre: 'ENCARGADO' } },
    accesoAnterior: '2026-10-07T18:00:00',
    fallidosDesdeAnterior: 2
  };

  function crear() {
    const f = TestBed.createComponent(PerfilComponent);
    f.detectChanges();
    return f;
  }

  beforeEach(() => {
    servicio = jasmine.createSpyObj('UsuarioService', ['miPerfil', 'actualizarMiPerfil']);
    servicio.miPerfil.and.returnValue(of(perfil));
    servicio.actualizarMiPerfil.and.returnValue(of({ ...perfil.usuario, telefono: '999888777' }));
    auth = jasmine.createSpyObj('AuthService', ['actualizarUsuario']);
    TestBed.configureTestingModule({
      imports: [PerfilComponent],
      providers: [{ provide: UsuarioService, useValue: servicio }, { provide: AuthService, useValue: auth }]
    });
  });

  it('CP-35: muestra el acceso anterior y advierte los intentos fallidos', () => {
    const f = crear();
    const texto = f.nativeElement.textContent;
    expect(texto).toContain('07/10/2026 a las 18:00');
    expect(texto).toContain('2 intento(s) fallido(s)');
    expect(f.componentInstance.iniciales()).toBe('LR');
  });

  it('CP-35: el área y el rol se muestran solo como lectura', () => {
    const f = crear();
    expect(f.nativeElement.querySelector('input[name="area"]')).toBeNull();
    expect(f.nativeElement.textContent).toContain('ENCARGADO');
  });

  it('CP-35: guarda solo los datos de contacto', () => {
    const c = crear().componentInstance;
    c.telefono = '999888777';
    c.guardar();
    const enviado = servicio.actualizarMiPerfil.calls.mostRecent().args[0];
    expect(enviado).toEqual({ nombres: 'Luis', apellidos: 'Rojas', correo: 'luis@a.pe', telefono: '999888777' });
    expect(auth.actualizarUsuario).toHaveBeenCalled();
    expect(c.mensajeExito).toContain('actualizaron');
  });

  it('CP-35: valida antes de enviar', () => {
    const c = crear().componentInstance;
    c.correo = 'no-es-correo';
    c.telefono = 'abc';
    c.password = 'corta';
    c.guardar();
    expect(Object.keys(c.errores)).toEqual(jasmine.arrayContaining(['correo', 'telefono', 'password', 'confirmarPassword']));
    expect(servicio.actualizarMiPerfil).not.toHaveBeenCalled();
  });

  it('descartar restablece los datos guardados', () => {
    const c = crear().componentInstance;
    c.nombres = 'Otro';
    c.descartar();
    expect(c.nombres).toBe('Luis');
  });

  it('informa el error de carga con opción de reintentar', () => {
    servicio.miPerfil.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const c = crear().componentInstance;
    expect(c.estado).toBe('error');
  });

  it('en el primer acceso no muestra advertencias', () => {
    servicio.miPerfil.and.returnValue(of({ ...perfil, accesoAnterior: null, fallidosDesdeAnterior: 0 }));
    expect(crear().nativeElement.textContent).toContain('primer inicio de sesión');
  });
});

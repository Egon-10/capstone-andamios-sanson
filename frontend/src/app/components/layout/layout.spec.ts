import { CUSTOM_ELEMENTS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterModule, provideRouter } from '@angular/router';

import { LayoutComponent } from './layout';
import { AuthService } from '../../services/auth.service';
import { SesionInactividadService } from '../../core/sesion-inactividad.service';
import { AlertaService } from '../../services/alerta.service';

/** HU-39: navegación por rol, menú del usuario accesible y aviso de inactividad. */
describe('LayoutComponent', () => {
  let rol = 'ADMINISTRADOR';
  const segundos = signal<number | null>(null);

  function crear() {
    const f = TestBed.createComponent(LayoutComponent);
    f.detectChanges();
    return f;
  }

  function enlaces(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('nav a')).map(a => (a.textContent ?? '').trim());
  }

  beforeEach(() => {
    segundos.set(null);
    const auth = {
      cerrarSesion: jasmine.createSpy('cerrarSesion'),
      get usuario() { return { nombre: 'Ana', correo: 'a@x.pe', rol: { nombre: rol } }; }
    };
    TestBed.configureTestingModule({
      imports: [LayoutComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: SesionInactividadService, useValue: jasmine.createSpyObj('SesionInactividadService', ['iniciar', 'detener', 'continuar'], { segundosRestantes: segundos }) },
        { provide: AlertaService, useValue: jasmine.createSpyObj('AlertaService', ['iniciar', 'detener']) }
      ]
    });
    // La campana de alertas tiene sus propias pruebas.
    TestBed.overrideComponent(LayoutComponent, { set: { imports: [RouterModule], schemas: [CUSTOM_ELEMENTS_SCHEMA] } });
  });

  it('muestra al administrador todas las opciones del menú', () => {
    rol = 'ADMINISTRADOR';
    const el: HTMLElement = crear().nativeElement;
    expect(enlaces(el)).toContain('Usuarios');
    expect(enlaces(el)).toContain('Auditoría');
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Menú principal');
  });

  it('oculta al encargado las opciones restringidas', () => {
    rol = 'ENCARGADO';
    const el: HTMLElement = crear().nativeElement;
    expect(enlaces(el)).not.toContain('Usuarios');
    expect(enlaces(el)).not.toContain('Valorización');
    expect(enlaces(el)).toContain('Movimientos');
  });

  it('en el celular abre el menú lateral con un botón y lo cierra con el velo', () => {
    const f = crear();
    const el: HTMLElement = f.nativeElement;
    const boton = el.querySelector('button.abrir-menu') as HTMLButtonElement;
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(boton.getAttribute('aria-controls')).toBe('menu-lateral');

    boton.click();
    f.detectChanges();
    expect(el.querySelector('#menu-lateral')?.classList).toContain('abierta');
    expect(boton.getAttribute('aria-label')).toBe('Cerrar el menú');

    (el.querySelector('button.velo-menu') as HTMLButtonElement).click();
    f.detectChanges();
    expect(el.querySelector('#menu-lateral')?.classList).not.toContain('abierta');
    expect(el.querySelector('button.velo-menu')).toBeNull();
  });

  it('abre el menú del usuario con un botón que informa si está desplegado', () => {
    const f = crear();
    const el: HTMLElement = f.nativeElement;
    const boton = el.querySelector('button.user-menu') as HTMLButtonElement;
    expect(boton.getAttribute('aria-expanded')).toBe('false');

    boton.click();
    f.detectChanges();

    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('#menu-usuario')?.textContent).toContain('Cerrar sesión');
  });

  it('avisa el cierre por inactividad y permite seguir conectado', () => {
    const f = crear();
    segundos.set(30);
    f.detectChanges();
    const aviso = f.nativeElement.querySelector('[role="alertdialog"]') as HTMLElement;
    expect(aviso.textContent).toContain('30 s');
    (aviso.querySelector('button') as HTMLButtonElement).click();
    expect(TestBed.inject(SesionInactividadService).continuar).toHaveBeenCalled();
  });
});

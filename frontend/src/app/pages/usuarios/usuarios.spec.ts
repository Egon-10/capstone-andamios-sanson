import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { UsuariosComponent } from './usuarios';
import { UsuarioService } from '../../services/usuario.service';
import { RolService } from '../../services/rol.service';
import { AuthService } from '../../services/auth.service';
import { ReporteService } from '../../services/reporte.service';
import { Usuario } from '../../models/usuario';
import { Pagina } from '../../models/pagina';

/** CP-31 (listado, HU-30) y CP-32 (activación y desactivación, HU-31). */
describe('UsuariosComponent', () => {
  let servicio: jasmine.SpyObj<UsuarioService>;

  const ana: Usuario = { id: 1, nombre: 'Ana', correo: 'ana@a.pe', nombreUsuario: 'ana', estado: 'ACTIVO' };
  const luis: Usuario = { id: 2, nombre: 'Luis', correo: 'luis@a.pe', nombreUsuario: 'luis', estado: 'ACTIVO' };
  const rosa: Usuario = { id: 3, nombre: 'Rosa', correo: 'rosa@a.pe', nombreUsuario: 'rosa', estado: 'INACTIVO' };

  function pagina(contenido: Usuario[]): Pagina<Usuario> {
    return { contenido, pagina: 0, tamano: 10, totalElementos: contenido.length, totalPaginas: 1, primera: true, ultima: true };
  }

  function crear() {
    const f = TestBed.createComponent(UsuariosComponent);
    f.detectChanges();
    return f;
  }

  beforeEach(() => {
    servicio = jasmine.createSpyObj('UsuarioService', ['buscar', 'cambiarEstado', 'registrar', 'actualizarParcial', 'restablecerPassword', 'desbloquear']);
    servicio.buscar.and.returnValue(of(pagina([ana, luis, rosa])));
    servicio.cambiarEstado.and.returnValue(of({ ...luis, estado: 'INACTIVO' }));
    TestBed.configureTestingModule({
      imports: [UsuariosComponent],
      providers: [
        { provide: UsuarioService, useValue: servicio },
        { provide: RolService, useValue: { listar: () => of([{ id: 1, nombre: 'ADMINISTRADOR' }]) } },
        { provide: AuthService, useValue: { usuario: { id: 1 } } },
        { provide: ReporteService, useValue: { descargar: () => of('r.pdf') } }
      ]
    });
  });

  it('CP-31: carga la primera página ordenada por nombre', () => {
    const f = crear();
    expect(servicio.buscar).toHaveBeenCalledWith(jasmine.objectContaining({ pagina: 0, tamano: 10, orden: 'nombre' }));
    expect(f.componentInstance.usuarios.length).toBe(3);
    expect(f.componentInstance.estadoLista).toBe('listo');
  });

  it('CP-31: el texto busca tras una pausa y los filtros vuelven a la página 1', fakeAsync(() => {
    const c = crear().componentInstance;
    servicio.buscar.calls.reset();
    c.filtro.texto = 'lu';
    c.buscarTexto('lu');
    tick(300);
    expect(servicio.buscar).toHaveBeenCalledWith(jasmine.objectContaining({ texto: 'lu', pagina: 0 }));
    c.filtro = { ...c.filtro, pagina: 4, estado: 'INACTIVO' };
    c.aplicarFiltros();
    expect(servicio.buscar.calls.mostRecent().args[0].pagina).toBe(0);
  }));

  it('CP-31: ordenar dos veces la misma columna invierte el sentido', () => {
    const c = crear().componentInstance;
    c.ordenar('fechaCreacion');
    expect(c.ordenAria('fechaCreacion')).toBe('ascending');
    c.ordenar('fechaCreacion');
    expect(c.ordenAria('fechaCreacion')).toBe('descending');
    expect(c.ordenAria('nombre')).toBe('none');
  });

  it('CP-31: paginación y tamaño de página', () => {
    const c = crear().componentInstance;
    c.irAPagina(2);
    expect(servicio.buscar.calls.mostRecent().args[0].pagina).toBe(2);
    c.cambiarTamano(50);
    expect(servicio.buscar.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ tamano: 50, pagina: 0 }));
  });

  it('CP-32: desactivar exige un motivo y confirma el cierre de sesiones', () => {
    const c = crear().componentInstance;
    c.pedirCambioEstado(luis);
    c.confirmarCambioEstado();
    expect(c.errorEstado).toContain('motivo');
    expect(servicio.cambiarEstado).not.toHaveBeenCalled();
    c.motivo = 'Cese laboral';
    c.confirmarCambioEstado();
    expect(servicio.cambiarEstado).toHaveBeenCalledWith(2, 'INACTIVO', 'Cese laboral');
    expect(c.mensajeExito).toContain('sesiones abiertas se cerraron');
    expect(c.usuarioCambiando).toBeNull();
  });

  it('CP-32: reactivar no pide motivo', () => {
    const c = crear().componentInstance;
    c.pedirCambioEstado(rosa);
    c.confirmarCambioEstado();
    expect(servicio.cambiarEstado).toHaveBeenCalledWith(3, 'ACTIVO', '');
  });

  it('CP-32: muestra el rechazo del servidor (último administrador)', () => {
    servicio.cambiarEstado.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 422, error: { estado: 422, mensaje: 'No se puede desactivar al único administrador activo' }
    })));
    const c = crear().componentInstance;
    c.pedirCambioEstado(luis);
    c.motivo = 'Prueba de rechazo';
    c.confirmarCambioEstado();
    expect(c.errorEstado).toContain('único administrador');
    expect(c.usuarioCambiando).not.toBeNull();
  });

  it('CP-32: no ofrece desactivar la propia cuenta', () => {
    const f = crear();
    expect(f.componentInstance.esUsuarioActual(ana)).toBeTrue();
    const filaAna: HTMLElement = f.nativeElement.querySelectorAll('tbody tr')[0];
    expect(filaAna.textContent).not.toContain('Desactivar');
  });

  it('distingue la lista vacía del error de carga', () => {
    servicio.buscar.and.returnValue(of(pagina([])));
    expect(crear().componentInstance.estadoLista).toBe('vacio');
    servicio.buscar.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const c = crear().componentInstance;
    expect(c.estadoLista).toBe('error');
    expect(c.errorLista).toContain('conexión');
  });

  it('quita los filtros', () => {
    const c = crear().componentInstance;
    c.filtro = { ...c.filtro, texto: 'x', estado: 'ACTIVO' };
    expect(c.hayFiltros()).toBeTrue();
    c.limpiarFiltros();
    expect(c.hayFiltros()).toBeFalse();
  });

  it('CP-37: restablece la contraseña y muestra la temporal una sola vez', () => {
    servicio.restablecerPassword.and.returnValue(of({ nombreUsuario: 'luis', passwordTemporal: 'Tmp#Clave2026xy' }));
    const c = crear().componentInstance;
    c.pedirRestablecimiento(luis);
    c.confirmarRestablecimiento();
    expect(servicio.restablecerPassword).toHaveBeenCalledWith(2);
    expect(c.restablecimiento?.passwordTemporal).toBe('Tmp#Clave2026xy');
    c.cerrarRestablecimiento();
    expect(c.restablecimiento).toBeNull();
  });

  it('CP-37: copia la temporal al portapapeles', async () => {
    const escribir = jasmine.createSpy('writeText').and.returnValue(Promise.resolve());
    spyOnProperty(navigator, 'clipboard', 'get').and.returnValue({ writeText: escribir } as unknown as Clipboard);
    const c = crear().componentInstance;
    c.restablecimiento = { nombreUsuario: 'luis', passwordTemporal: 'Tmp#Clave2026xy' };
    c.copiarTemporal();
    await Promise.resolve();
    expect(escribir).toHaveBeenCalledWith('Tmp#Clave2026xy');
  });

  it('CP-36: desbloquea una cuenta bloqueada', () => {
    servicio.desbloquear.and.returnValue(of({ ...luis, bloqueado: false }));
    const c = crear().componentInstance;
    c.desbloquear({ ...luis, bloqueado: true });
    expect(servicio.desbloquear).toHaveBeenCalledWith(2);
    expect(c.mensajeExito).toContain('desbloqueó');
  });

  it('CP-38: el registro exige una contraseña que cumpla la política', () => {
    const c = crear().componentInstance;
    c.form = { ...c.form, nombres: 'Ana', apellidos: 'Pérez', correo: 'ana@a.pe', rolId: 1, area: 'LOGISTICA',
      numeroDocumento: '45678912', nombreUsuario: 'ana.perez', password: 'Clave2026', confirmarPassword: 'Clave2026' };
    c.guardar();
    expect(c.errores['password']).toContain('requisitos');
    expect(servicio.registrar).not.toHaveBeenCalled();
  });

  it('muestra la cuenta bloqueada y la clave temporal en la lista', () => {
    servicio.buscar.and.returnValue(of(pagina([{ ...luis, bloqueado: true, bloqueadoHasta: '2026-10-08T15:30:00', debeCambiarPassword: true }])));
    const texto = crear().nativeElement.textContent;
    expect(texto).toContain('Bloqueada hasta 15:30');
    expect(texto).toContain('Clave temporal');
    expect(texto).toContain('Desbloquear');
  });
});

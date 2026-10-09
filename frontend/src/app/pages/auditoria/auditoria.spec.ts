import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { AuditoriaComponent } from './auditoria';
import { AuditoriaService } from '../../services/auditoria.service';
import { AuthService } from '../../services/auth.service';
import { UsuarioService } from '../../services/usuario.service';
import { ReporteService } from '../../services/reporte.service';
import { Pagina } from '../../models/pagina';
import { RegistroAcceso, RegistroAuditoria } from '../../models/auditoria';

/** CP-33 (accesos, HU-32) y CP-34 (auditoría con filtros, HU-33). */
describe('AuditoriaComponent', () => {
  let servicio: jasmine.SpyObj<AuditoriaService>;
  let reportes: jasmine.SpyObj<ReporteService>;
  let rol = 'ADMINISTRADOR';

  function pagina<T>(contenido: T[]): Pagina<T> {
    return { contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: 1, primera: true, ultima: true };
  }
  const accion: RegistroAuditoria = { id: 1, fecha: '2026-10-08T10:00:00', accion: 'MOVIMIENTO_ANULADO', detalle: 'x'.repeat(120), usuario: 'Ana' };
  const acceso: RegistroAcceso = { id: 2, fecha: '2026-10-08T09:00:00', identificador: 'admin', resultado: 'FALLIDO', motivo: 'Contraseña incorrecta' };

  function crear() {
    const f = TestBed.createComponent(AuditoriaComponent);
    f.detectChanges();
    return f;
  }

  beforeEach(() => {
    servicio = jasmine.createSpyObj('AuditoriaService', ['buscar', 'accesos']);
    servicio.buscar.and.returnValue(of(pagina([accion])));
    servicio.accesos.and.returnValue(of(pagina([acceso])));
    reportes = jasmine.createSpyObj('ReporteService', ['descargar']);
    reportes.descargar.and.returnValue(of('r.pdf'));
    TestBed.configureTestingModule({
      imports: [AuditoriaComponent],
      providers: [
        { provide: AuditoriaService, useValue: servicio },
        { provide: ReporteService, useValue: reportes },
        { provide: AuthService, useValue: { get rol() { return rol; } } },
        { provide: UsuarioService, useValue: { opciones: () => of([{ id: 1, nombre: 'Ana' }]) } }
      ]
    });
  });

  it('CP-34: carga la primera página de acciones y la muestra legible', () => {
    const f = crear();
    expect(f.componentInstance.estado).toBe('listo');
    expect(f.nativeElement.textContent).toContain('Movimiento anulado');
    expect(f.componentInstance.usuarios.length).toBe(1);
  });

  it('CP-34: combina los filtros y vuelve a la primera página', () => {
    const c = crear().componentInstance;
    c.filtro = { ...c.filtro, usuarioId: 1, desde: '2026-10-01', pagina: 3 };
    c.buscar();
    const enviado = servicio.buscar.calls.mostRecent().args[0];
    expect(enviado.usuarioId).toBe(1);
    expect(enviado.desde).toBe('2026-10-01');
    expect(enviado.pagina).toBe(0);
    expect('resultado' in enviado).toBeFalse();
  });

  it('CP-34: la búsqueda por texto espera a que se deje de escribir', fakeAsync(() => {
    const c = crear().componentInstance;
    servicio.buscar.calls.reset();
    c.escribir();
    c.escribir();
    tick(299);
    expect(servicio.buscar).not.toHaveBeenCalled();
    tick(1);
    expect(servicio.buscar).toHaveBeenCalledTimes(1);
  }));

  it('CP-34: no consulta con un rango invertido', () => {
    const c = crear().componentInstance;
    servicio.buscar.calls.reset();
    c.filtro = { ...c.filtro, desde: '2026-10-08', hasta: '2026-10-01' };
    c.consultar();
    expect(servicio.buscar).not.toHaveBeenCalled();
    expect(c.errorFiltro).toContain('posterior');
  });

  it('CP-33: el administrador ve la pestaña de accesos con el resultado en texto', () => {
    const f = crear();
    f.componentInstance.cambiarVista('accesos');
    f.detectChanges();
    expect(servicio.accesos).toHaveBeenCalled();
    expect(f.nativeElement.textContent).toContain('Fallido');
    expect(f.componentInstance.claseResultado('BLOQUEADO')).toBe('aviso');
    expect(f.componentInstance.iconoResultado('EXITOSO')).toBe('fa-circle-check');
  });

  it('CP-33: el gerente no ve la pestaña de accesos', () => {
    rol = 'GERENTE';
    const f = crear();
    expect(f.nativeElement.querySelector('[role="tablist"]')).toBeNull();
    rol = 'ADMINISTRADOR';
  });

  it('distingue el vacío del error', () => {
    servicio.buscar.and.returnValue(of(pagina<RegistroAuditoria>([])));
    expect(crear().componentInstance.estado).toBe('vacio');
    servicio.buscar.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(crear().componentInstance.estado).toBe('error');
  });

  it('exporta la bitácora visible con sus filtros, sin la paginación', () => {
    const c = crear().componentInstance;
    c.filtro = { ...c.filtro, texto: 'anul' };
    c.exportar('xlsx');
    const [tipo, formato, filtros] = reportes.descargar.calls.mostRecent().args;
    expect(tipo).toBe('auditoria');
    expect(formato).toBe('xlsx');
    expect((filtros as Record<string, unknown>)['texto']).toBe('anul');
    expect('pagina' in (filtros as object)).toBeFalse();
  });

  it('expande y contrae un detalle largo, y limpia los filtros', () => {
    const c = crear().componentInstance;
    c.alternarDetalle(1);
    expect(c.expandido).toBe(1);
    c.alternarDetalle(1);
    expect(c.expandido).toBeNull();
    c.filtro = { ...c.filtro, texto: 'x' };
    expect(c.hayFiltros()).toBeTrue();
    c.limpiar();
    expect(c.hayFiltros()).toBeFalse();
  });
});

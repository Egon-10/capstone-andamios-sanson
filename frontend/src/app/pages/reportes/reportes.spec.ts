import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { ReportesComponent, hoyEnLima } from './reportes';
import { AuthService } from '../../services/auth.service';
import { CategoriaService } from '../../services/categoria.service';
import { ProductoService } from '../../services/producto.service';
import { RolService } from '../../services/rol.service';
import { UsuarioService } from '../../services/usuario.service';
import { ReporteService } from '../../services/reporte.service';

/** CP-27, CP-28 y CP-29: pantalla de reportes. */
describe('ReportesComponent', () => {
  let reportes: jasmine.SpyObj<ReporteService>;
  let rol = 'ADMINISTRADOR';

  function crear(): ReportesComponent {
    const f = TestBed.createComponent(ReportesComponent);
    f.detectChanges();
    return f.componentInstance;
  }

  beforeEach(() => {
    reportes = jasmine.createSpyObj('ReporteService', ['descargar', 'descargarKardex']);
    reportes.descargar.and.returnValue(of('reporte-movimientos.pdf'));
    reportes.descargarKardex.and.returnValue(of('reporte-kardex-7.xlsx'));
    TestBed.configureTestingModule({
      imports: [ReportesComponent],
      providers: [
        { provide: ReporteService, useValue: reportes },
        { provide: AuthService, useValue: { get rol() { return rol; } } },
        { provide: CategoriaService, useValue: { listar: () => of([{ id: 1, nombre: 'Andamios' }]) } },
        { provide: ProductoService, useValue: { listar: () => of([{ id: 7, sku: 'AND-1', nombre: 'Marco' }]) } },
        { provide: RolService, useValue: { listar: () => of([]) } },
        { provide: UsuarioService, useValue: { opciones: () => of([]) } }
      ]
    });
  });

  it('CP-27: el encargado no ve los reportes de gestión ni las bitácoras', () => {
    rol = 'ENCARGADO';
    const tipos = crear().opciones.map(o => o.tipo);
    expect(tipos).toContain('movimientos');
    expect(tipos).not.toContain('valorizacion');
    expect(tipos).not.toContain('accesos');
  });

  it('CP-27: solo el administrador ve la bitácora de accesos', () => {
    rol = 'GERENTE';
    expect(crear().opciones.map(o => o.tipo)).not.toContain('accesos');
    rol = 'ADMINISTRADOR';
  });

  it('CP-27: descarga el reporte con sus filtros e informa el archivo', () => {
    const c = crear();
    c.tipoMovimiento = 'SALIDA';
    c.desde = '2026-10-01';
    c.descargar('pdf');
    expect(reportes.descargar).toHaveBeenCalledWith('movimientos', 'pdf', { tipo: 'SALIDA', desde: '2026-10-01', hasta: '' });
    expect(c.mensaje).toContain('reporte-movimientos.pdf');
  });

  it('CP-28: el kardex exige un producto y usa su ruta', () => {
    const c = crear();
    c.elegir('kardex');
    c.descargar('xlsx');
    expect(c.error).toContain('producto');
    c.productoId = 7;
    c.descargar('xlsx');
    expect(reportes.descargarKardex).toHaveBeenCalledWith(7, 'xlsx', { desde: '', hasta: '' });
  });

  it('CP-29: no acepta un corte futuro ni un rango invertido', () => {
    const c = crear();
    c.elegir('valorizacion');
    c.fechaCorte = '2999-12-31';
    c.descargar('pdf');
    expect(c.error).toContain('posterior a hoy');
    c.elegir('auditoria');
    c.desde = '2026-10-08';
    c.hasta = '2026-10-01';
    c.descargar('pdf');
    expect(c.error).toContain('posterior a la final');
    expect(reportes.descargar).not.toHaveBeenCalled();
  });

  it('muestra el mensaje de error que envía el servidor', async () => {
    const cuerpo = new Blob([JSON.stringify({ estado: 403, mensaje: 'Sin permiso' })]);
    reportes.descargar.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403, error: cuerpo })));
    const c = crear();
    c.descargar('pdf');
    for (let i = 0; i < 20 && !c.error; i++) { await new Promise(r => setTimeout(r, 10)); }
    expect(c.error).toBe('Sin permiso');
    expect(c.descargando).toBeNull();
  });

  it('arma los filtros de cada reporte y los limpia', () => {
    const c = crear();
    c.elegir('usuarios');
    c.estado = 'ACTIVO';
    expect(c.filtros()).toEqual({ texto: '', rolId: null, estado: 'ACTIVO' });
    c.elegir('accesos');
    c.resultado = 'FALLIDO';
    expect((c.filtros() as { resultado: string }).resultado).toBe('FALLIDO');
    c.elegir('productos');
    expect(c.filtros()).toEqual({ categoriaId: null });
    c.elegir('stock-critico');
    expect(c.filtros()).toEqual({});
    expect(c.usaPeriodo()).toBeFalse();
    c.limpiar();
    expect(c.resultado).toBe('');
  });

  it('calcula la fecha de hoy en Lima', () => {
    // 03:00 UTC del 9 de octubre aún es 8 de octubre en Lima.
    expect(hoyEnLima(new Date('2026-10-09T03:00:00Z'))).toBe('2026-10-08');
  });
});

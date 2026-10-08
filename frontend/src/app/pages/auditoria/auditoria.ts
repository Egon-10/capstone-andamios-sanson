import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, Subscription, debounceTime } from 'rxjs';

import { AuditoriaService } from '../../services/auditoria.service';
import { AuthService } from '../../services/auth.service';
import { UsuarioService } from '../../services/usuario.service';
import { FormatoReporte, ReporteService } from '../../services/reporte.service';
import { mensajeDeError, mensajeDeErrorEnBlob } from '../../core/errores';
import { FiltroBitacora, RegistroAcceso, RegistroAuditoria, UsuarioOpcion } from '../../models/auditoria';
import { Pagina } from '../../models/pagina';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';
import { PaginacionComponent } from '../../components/paginacion/paginacion';
import { hoyEnLima } from '../reportes/reportes';

type Vista = 'acciones' | 'accesos';

/** Presentación de cada resultado de acceso: color, icono y texto, nunca solo color. */
const RESULTADOS: Record<RegistroAcceso['resultado'], { clase: string; icono: string; texto: string }> = {
  EXITOSO: { clase: 'exito', icono: 'fa-circle-check', texto: 'Exitoso' },
  FALLIDO: { clase: 'peligro', icono: 'fa-circle-xmark', texto: 'Fallido' },
  BLOQUEADO: { clase: 'aviso', icono: 'fa-lock', texto: 'Bloqueado' }
};
type Estado = 'cargando' | 'error' | 'vacio' | 'listo';

/**
 * HU-33: consulta de la bitácora de auditoría con filtros combinados.
 * HU-32: bitácora de accesos exitosos y fallidos (solo administrador).
 *
 * Los filtros se aplican en el servidor y el resultado llega paginado: la
 * bitácora crece todos los días y traerla completa al navegador dejaría de
 * funcionar a los pocos meses.
 */
@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [DatePipe, FormsModule, EstadoVistaComponent, PaginacionComponent],
  templateUrl: './auditoria.html',
  styleUrl: './auditoria.css'
})
export class AuditoriaComponent implements OnInit, OnDestroy {

  private readonly auditoria = inject(AuditoriaService);
  private readonly auth = inject(AuthService);
  private readonly usuarioService = inject(UsuarioService);
  private readonly reportes = inject(ReporteService);

  vista: Vista = 'acciones';
  readonly hoy = hoyEnLima();

  filtro: FiltroBitacora = { texto: '', usuarioId: null, resultado: '', desde: '', hasta: '', pagina: 0, tamano: 20 };
  usuarios: UsuarioOpcion[] = [];

  acciones?: Pagina<RegistroAuditoria>;
  accesos?: Pagina<RegistroAcceso>;
  estado: Estado = 'cargando';
  error = '';
  errorFiltro = '';
  descargando: FormatoReporte | null = null;

  /** Registro cuyo detalle completo se está mostrando. */
  expandido: number | null = null;

  private readonly escritura = new Subject<void>();
  private suscripcion?: Subscription;

  get esAdministrador(): boolean {
    return this.auth.rol === 'ADMINISTRADOR';
  }

  ngOnInit(): void {
    this.usuarioService.opciones().subscribe({ next: u => (this.usuarios = u), error: () => {} });
    this.suscripcion = this.escritura.pipe(debounceTime(300)).subscribe(() => this.buscar());
    this.consultar();
  }

  ngOnDestroy(): void {
    this.suscripcion?.unsubscribe();
  }

  cambiarVista(vista: Vista): void {
    if (vista === this.vista) {
      return;
    }
    this.vista = vista;
    this.filtro = { ...this.filtro, resultado: '', pagina: 0 };
    this.expandido = null;
    this.consultar();
  }

  escribir(): void {
    this.escritura.next();
  }

  buscar(): void {
    this.filtro = { ...this.filtro, pagina: 0 };
    this.consultar();
  }

  irAPagina(n: number): void {
    this.filtro = { ...this.filtro, pagina: n };
    this.consultar();
  }

  cambiarTamano(t: number): void {
    this.filtro = { ...this.filtro, tamano: t, pagina: 0 };
    this.consultar();
  }

  limpiar(): void {
    this.filtro = { texto: '', usuarioId: null, resultado: '', desde: '', hasta: '', pagina: 0, tamano: this.filtro.tamano };
    this.consultar();
  }

  hayFiltros(): boolean {
    const f = this.filtro;
    return !!(f.texto || f.usuarioId || f.resultado || f.desde || f.hasta);
  }

  consultar(): void {
    this.errorFiltro = '';
    if (this.filtro.desde && this.filtro.hasta && this.filtro.desde > this.filtro.hasta) {
      this.errorFiltro = 'La fecha inicial no puede ser posterior a la final.';
      return;
    }
    this.estado = 'cargando';
    const alTerminar = (total: number) => (this.estado = total === 0 ? 'vacio' : 'listo');
    const alFallar = (e: HttpErrorResponse) => {
      this.estado = 'error';
      this.error = mensajeDeError(e, 'Revise la conexión e intente de nuevo.');
    };

    if (this.vista === 'acciones') {
      const { resultado: _resultado, ...filtro } = this.filtro;
      this.auditoria.buscar(filtro).subscribe({
        next: p => { this.acciones = p; alTerminar(p.contenido.length); },
        error: alFallar
      });
    } else {
      this.auditoria.accesos(this.filtro).subscribe({
        next: p => { this.accesos = p; alTerminar(p.contenido.length); },
        error: alFallar
      });
    }
  }

  alternarDetalle(id: number): void {
    this.expandido = this.expandido === id ? null : id;
  }

  exportar(formato: FormatoReporte): void {
    this.descargando = formato;
    const { pagina: _p, tamano: _t, ...filtros } = this.filtro;
    this.reportes.descargar(this.vista === 'acciones' ? 'auditoria' : 'accesos', formato, filtros).subscribe({
      next: () => (this.descargando = null),
      error: (e: HttpErrorResponse) => {
        this.descargando = null;
        // El cuerpo del error llega como Blob y se lee de forma asincrona.
        void mensajeDeErrorEnBlob(e, 'No se pudo generar el reporte.').then(m => (this.errorFiltro = m));
      }
    });
  }

  claseResultado(r: RegistroAcceso['resultado']): string {
    return RESULTADOS[r].clase;
  }

  iconoResultado(r: RegistroAcceso['resultado']): string {
    return RESULTADOS[r].icono;
  }

  textoResultado(r: RegistroAcceso['resultado']): string {
    return RESULTADOS[r].texto;
  }

  /** Las acciones se guardan como CÓDIGO_EN_MAYÚSCULAS; se muestran legibles. */
  accionLegible(accion: string): string {
    const texto = accion.replaceAll('_', ' ').toLowerCase();
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }
}

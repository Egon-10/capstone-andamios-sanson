import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Rol } from '../../models/rol';
import { ErrorApi } from '../../models/respuesta-login';
import { FiltroUsuarios, Restablecimiento, Usuario, UsuarioActualizacion, UsuarioRegistro } from '../../models/usuario';
import { Pagina } from '../../models/pagina';
import { RolService } from '../../services/rol.service';
import { UsuarioService } from '../../services/usuario.service';
import { AuthService } from '../../services/auth.service';
import { FormatoReporte, ReporteService } from '../../services/reporte.service';
import { mensajeDeError, mensajeDeErrorEnBlob } from '../../core/errores';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';
import { PaginacionComponent } from '../../components/paginacion/paginacion';
import { PoliticaContrasenaComponent } from '../../components/politica-contrasena/politica-contrasena';
import { faltas } from '../../core/politica-contrasena';

const SOLO_LETRAS = /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$/;
const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * HU-43: registro de usuarios (12 campos y turno, CAM-02).
 * HU-07: edición parcial mediante PATCH.
 * HU-30: listado con búsqueda, filtros, orden y paginación en el servidor.
 * HU-31: activación y desactivación con confirmación y motivo.
 * HU-35: desbloqueo manual. HU-36: restablecimiento con contraseña temporal.
 * HU-37: la contraseña inicial cumple la política de complejidad.
 * Las validaciones del cliente replican las del servidor, que es quien decide.
 */
@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    EstadoVistaComponent,
    PaginacionComponent,
    PoliticaContrasenaComponent
  ],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css'
})
export class UsuariosComponent implements OnInit, OnDestroy {

  private readonly usuarioService = inject(UsuarioService);
  private readonly rolService = inject(RolService);
  private readonly auth = inject(AuthService);
  private readonly reportes = inject(ReporteService);

  readonly tiposDocumento = [
    { valor: 'DNI', texto: 'DNI' },
    { valor: 'CE', texto: 'Carné de extranjería' },
    { valor: 'PASAPORTE', texto: 'Pasaporte' }
  ];
  readonly areas = [
    { valor: 'LOGISTICA', texto: 'Logística' },
    { valor: 'PRODUCCION', texto: 'Producción' },
    { valor: 'COMERCIAL', texto: 'Comercial' },
    { valor: 'ADMINISTRACION', texto: 'Administración' }
  ];
  readonly turnos = [
    { valor: 'MANANA', texto: 'Mañana' },
    { valor: 'TARDE', texto: 'Tarde' },
    { valor: 'NOCHE', texto: 'Noche' }
  ];

  roles: Rol[] = [];

  // --- HU-30: listado ---
  filtro: FiltroUsuarios = { texto: '', rolId: null, estado: '', pagina: 0, tamano: 10, orden: 'nombre', descendente: false };
  pagina?: Pagina<Usuario>;
  estadoLista: 'cargando' | 'error' | 'vacio' | 'listo' = 'cargando';
  errorLista = '';
  private readonly busqueda = new Subject<string>();
  private suscripcionBusqueda?: Subscription;

  // --- HU-31: cambio de estado ---
  usuarioCambiando: Usuario | null = null;
  motivo = '';
  cambiandoEstado = false;
  errorEstado = '';

  descargando: FormatoReporte | null = null;

  // --- HU-36: restablecimiento ---
  usuarioRestableciendo: Usuario | null = null;
  restablecimiento: Restablecimiento | null = null;
  restableciendo = false;
  errorRestablecer = '';
  copiado = false;

  form: UsuarioRegistro = this.formularioVacio();
  idEditando: number | null = null;

  errores: Record<string, string> = {};
  mensajeError = '';
  mensajeExito = '';
  mostrarPassword = false;
  guardando = false;

  get editando(): boolean {
    return this.idEditando !== null;
  }

  get usuarios(): Usuario[] {
    return this.pagina?.contenido ?? [];
  }

  ngOnInit(): void {
    this.listarUsuarios();
    this.rolService.listar().subscribe(data => (this.roles = data));
    // La búsqueda por texto espera a que el usuario deje de escribir, para no
    // consultar al servidor en cada tecla.
    this.suscripcionBusqueda = this.busqueda
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.irAPagina(0));
  }

  ngOnDestroy(): void {
    this.suscripcionBusqueda?.unsubscribe();
  }

  listarUsuarios(): void {
    this.estadoLista = 'cargando';
    this.usuarioService.buscar(this.filtro).subscribe({
      next: p => {
        this.pagina = p;
        this.estadoLista = p.contenido.length === 0 ? 'vacio' : 'listo';
      },
      error: (e: HttpErrorResponse) => {
        this.estadoLista = 'error';
        this.errorLista = mensajeDeError(e, 'Revise la conexión e intente de nuevo.');
      }
    });
  }

  buscarTexto(texto: string): void {
    this.busqueda.next(texto);
  }

  aplicarFiltros(): void {
    this.irAPagina(0);
  }

  irAPagina(numero: number): void {
    this.filtro = { ...this.filtro, pagina: numero };
    this.listarUsuarios();
  }

  cambiarTamano(tamano: number): void {
    this.filtro = { ...this.filtro, tamano, pagina: 0 };
    this.listarUsuarios();
  }

  /** Ordena por la columna; un segundo clic invierte el sentido. */
  ordenar(campo: string): void {
    const descendente = this.filtro.orden === campo ? !this.filtro.descendente : false;
    this.filtro = { ...this.filtro, orden: campo, descendente, pagina: 0 };
    this.listarUsuarios();
  }

  ordenAria(campo: string): 'ascending' | 'descending' | 'none' {
    if (this.filtro.orden !== campo) {
      return 'none';
    }
    return this.filtro.descendente ? 'descending' : 'ascending';
  }

  hayFiltros(): boolean {
    return !!(this.filtro.texto || this.filtro.rolId || this.filtro.estado);
  }

  limpiarFiltros(): void {
    this.filtro = { ...this.filtro, texto: '', rolId: null, estado: '', pagina: 0 };
    this.listarUsuarios();
  }

  esUsuarioActual(u: Usuario): boolean {
    return u.id === this.auth.usuario?.id;
  }

  // --- HU-31 ---

  pedirCambioEstado(u: Usuario): void {
    this.usuarioCambiando = u;
    this.motivo = '';
    this.errorEstado = '';
  }

  @HostListener('document:keydown.escape')
  cancelarCambioEstado(): void {
    this.usuarioCambiando = null;
    if (!this.restablecimiento) {
      this.usuarioRestableciendo = null;
    }
  }

  // --- HU-36 ---

  pedirRestablecimiento(u: Usuario): void {
    this.usuarioRestableciendo = u;
    this.restablecimiento = null;
    this.errorRestablecer = '';
    this.copiado = false;
  }

  confirmarRestablecimiento(): void {
    const u = this.usuarioRestableciendo;
    if (!u?.id) {
      return;
    }
    this.restableciendo = true;
    this.usuarioService.restablecerPassword(u.id).subscribe({
      next: r => {
        this.restableciendo = false;
        this.restablecimiento = r;
        this.listarUsuarios();
      },
      error: (e: HttpErrorResponse) => {
        this.restableciendo = false;
        this.errorRestablecer = mensajeDeError(e, 'No se pudo restablecer la contraseña.');
      }
    });
  }

  copiarTemporal(): void {
    const clave = this.restablecimiento?.passwordTemporal;
    if (!clave) {
      return;
    }
    navigator.clipboard?.writeText(clave).then(() => (this.copiado = true), () => (this.copiado = false));
  }

  /** Al cerrar, la contraseña temporal se descarta de la memoria de la pantalla. */
  cerrarRestablecimiento(): void {
    this.usuarioRestableciendo = null;
    this.restablecimiento = null;
  }

  // --- HU-35 ---

  desbloquear(u: Usuario): void {
    if (!u.id) {
      return;
    }
    this.usuarioService.desbloquear(u.id).subscribe({
      next: () => {
        this.mensajeExito = `Se desbloqueó la cuenta ${u.nombreUsuario ?? u.correo}.`;
        this.listarUsuarios();
      },
      error: (e: HttpErrorResponse) => (this.mensajeError = mensajeDeError(e, 'No se pudo desbloquear la cuenta.'))
    });
  }

  confirmarCambioEstado(): void {
    const u = this.usuarioCambiando;
    if (!u?.id) {
      return;
    }
    const nuevo = u.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    if (nuevo === 'INACTIVO' && this.motivo.trim().length < 5) {
      this.errorEstado = 'Indique el motivo de la desactivación (mínimo 5 caracteres).';
      return;
    }
    this.cambiandoEstado = true;
    this.usuarioService.cambiarEstado(u.id, nuevo, this.motivo.trim()).subscribe({
      next: () => {
        this.cambiandoEstado = false;
        this.usuarioCambiando = null;
        this.mensajeExito = nuevo === 'INACTIVO'
          ? `Se desactivó la cuenta ${u.nombreUsuario ?? u.correo}. Sus sesiones abiertas se cerraron.`
          : `Se reactivó la cuenta ${u.nombreUsuario ?? u.correo}.`;
        this.listarUsuarios();
      },
      error: (e: HttpErrorResponse) => {
        this.cambiandoEstado = false;
        this.errorEstado = mensajeDeError(e, 'No se pudo cambiar el estado de la cuenta.');
      }
    });
  }

  exportar(formato: FormatoReporte): void {
    this.descargando = formato;
    const { texto, rolId, estado } = this.filtro;
    this.reportes.descargar('usuarios', formato, { texto, rolId, estado }).subscribe({
      next: () => (this.descargando = null),
      error: (e: HttpErrorResponse) => {
        this.descargando = null;
        // El cuerpo del error llega como Blob y se lee de forma asincrona.
        void mensajeDeErrorEnBlob(e, 'No se pudo generar el reporte.').then(m => (this.mensajeError = m));
      }
    });
  }

  nombreRol(id: number | null): string {
    return this.roles.find(r => r.id === id)?.nombre ?? '';
  }

  limpiarError(campo: string): void {
    delete this.errores[campo];
    this.mensajeError = '';
  }

  guardar(): void {
    this.mensajeError = '';
    this.mensajeExito = '';
    this.errores = this.validar();

    if (Object.keys(this.errores).length > 0) {
      this.mensajeError = 'Revise los campos marcados.';
      return;
    }

    this.guardando = true;

    if (this.editando) {
      this.usuarioService
        .actualizarParcial(this.idEditando!, this.cambios())
        .subscribe({
          next: () => this.finalizar('Usuario actualizado correctamente.'),
          error: (e: HttpErrorResponse) => this.mostrarErrorServidor(e)
        });
    } else {
      this.usuarioService
        .registrar({ ...this.form, nombreUsuario: this.form.nombreUsuario.trim() })
        .subscribe({
          next: () => this.finalizar('Usuario registrado correctamente.'),
          error: (e: HttpErrorResponse) => this.mostrarErrorServidor(e)
        });
    }
  }

  editar(usuario: Usuario): void {
    this.idEditando = usuario.id ?? null;
    this.form = {
      nombres: usuario.nombre ?? '',
      apellidos: usuario.apellidos ?? '',
      tipoDocumento: usuario.tipoDocumento ?? '',
      numeroDocumento: usuario.numeroDocumento ?? '',
      correo: usuario.correo ?? '',
      telefono: usuario.telefono ?? '',
      nombreUsuario: usuario.nombreUsuario ?? '',
      password: '',
      confirmarPassword: '',
      rolId: usuario.rol?.id ?? null,
      area: usuario.area ?? '',
      turno: usuario.turno ?? ''
    };
    this.errores = {};
    this.mensajeError = '';
    this.mensajeExito = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  limpiar(): void {
    this.form = this.formularioVacio();
    this.idEditando = null;
    this.errores = {};
    this.mensajeError = '';
  }

  private validar(): Record<string, string> {
    const f = this.form;
    const e: Record<string, string> = {};

    if (!f.nombres.trim() || f.nombres.trim().length < 2 || !SOLO_LETRAS.test(f.nombres.trim())) {
      e['nombres'] = 'Solo letras y espacios; mínimo 2 caracteres.';
    }
    if (!f.apellidos.trim() || f.apellidos.trim().length < 2 || !SOLO_LETRAS.test(f.apellidos.trim())) {
      e['apellidos'] = 'Solo letras y espacios; mínimo 2 caracteres.';
    }
    if (!CORREO.test(f.correo.trim())) {
      e['correo'] = 'Ingrese un correo válido.';
    }
    if (f.telefono && !/^\d{1,15}$/.test(f.telefono)) {
      e['telefono'] = 'Solo dígitos (máximo 15).';
    }
    if (!f.rolId) {
      e['rolId'] = 'Seleccione un rol.';
    }
    if (!f.area) {
      e['area'] = 'Seleccione el área.';
    }

    if (!this.editando) {
      if (!f.tipoDocumento) {
        e['tipoDocumento'] = 'Seleccione el tipo de documento.';
      }
      if (f.tipoDocumento === 'DNI' && !/^\d{8}$/.test(f.numeroDocumento)) {
        e['numeroDocumento'] = 'El DNI debe tener 8 dígitos.';
      } else if (f.tipoDocumento !== 'DNI' && !/^[A-Za-z0-9]{6,20}$/.test(f.numeroDocumento)) {
        e['numeroDocumento'] = 'Entre 6 y 20 caracteres alfanuméricos.';
      }
      if (!/^\S{4,30}$/.test(f.nombreUsuario.trim())) {
        e['nombreUsuario'] = 'Entre 4 y 30 caracteres, sin espacios.';
      }
    }

    // HU-37: la contraseña solo se fija al crear la cuenta; después se restablece.
    const exigePassword = !this.editando;
    if (exigePassword && faltas(f.password, f.nombreUsuario, f.numeroDocumento).length > 0) {
      e['password'] = 'La contraseña no cumple todos los requisitos de la lista.';
    }
    if (exigePassword && f.password !== f.confirmarPassword) {
      e['confirmarPassword'] = 'La contraseña y su confirmación no coinciden.';
    }
    return e;
  }

  /** Solo se envían los campos editables; la contraseña no se edita aquí (HU-36). */
  private cambios(): UsuarioActualizacion {
    const f = this.form;
    const cambios: UsuarioActualizacion = {
      nombres: f.nombres.trim(),
      apellidos: f.apellidos.trim(),
      correo: f.correo.trim(),
      telefono: f.telefono,
      area: f.area,
      turno: f.turno,
      rolId: f.rolId ?? undefined
    };
    return cambios;
  }

  private finalizar(mensaje: string): void {
    this.guardando = false;
    this.limpiar();
    this.mensajeExito = mensaje;
    this.listarUsuarios();
    setTimeout(() => (this.mensajeExito = ''), 3000);
  }

  private mostrarErrorServidor(error: HttpErrorResponse): void {
    this.guardando = false;
    const cuerpo = error.error as ErrorApi | null;
    this.errores = { ...(cuerpo?.errores ?? {}) };
    this.mensajeError = cuerpo?.mensaje ?? 'No se pudo completar la operación.';
  }

  private formularioVacio(): UsuarioRegistro {
    return {
      nombres: '',
      apellidos: '',
      tipoDocumento: 'DNI',
      numeroDocumento: '',
      correo: '',
      telefono: '',
      nombreUsuario: '',
      password: '',
      confirmarPassword: '',
      rolId: null,
      area: '',
      turno: ''
    };
  }
}

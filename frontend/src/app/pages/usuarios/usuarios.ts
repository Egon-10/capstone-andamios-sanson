import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Rol } from '../../models/rol';
import { ErrorApi } from '../../models/respuesta-login';
import { Usuario, UsuarioActualizacion, UsuarioRegistro } from '../../models/usuario';
import { RolService } from '../../services/rol.service';
import { UsuarioService } from '../../services/usuario.service';
import { AuthService } from '../../services/auth.service';

const SOLO_LETRAS = /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$/;
const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/;

/**
 * HU-43: registro de usuarios (12 campos y turno, CAM-02).
 * HU-07: edición parcial mediante PATCH.
 * Las validaciones del cliente replican las del servidor, que es quien decide.
 */
@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css'
})
export class UsuariosComponent implements OnInit {

  private readonly usuarioService = inject(UsuarioService);
  private readonly rolService = inject(RolService);
  private readonly auth = inject(AuthService);

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

  usuarios: Usuario[] = [];
  roles: Rol[] = [];

  form: UsuarioRegistro = this.formularioVacio();
  estado = 'ACTIVO';
  idEditando: number | null = null;

  errores: Record<string, string> = {};
  mensajeError = '';
  mensajeExito = '';
  mostrarPassword = false;
  guardando = false;

  get editando(): boolean {
    return this.idEditando !== null;
  }

  ngOnInit(): void {
    this.listarUsuarios();
    this.rolService.listar().subscribe(data => (this.roles = data));
  }

  listarUsuarios(): void {
    this.usuarioService.listar().subscribe(data => (this.usuarios = data));
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
    this.estado = usuario.estado ?? 'ACTIVO';
    this.errores = {};
    this.mensajeError = '';
    this.mensajeExito = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  eliminar(usuario: Usuario): void {
    if (usuario.id === this.auth.usuario?.id) {
      this.mensajeError = 'No puede eliminar su propia cuenta.';
      return;
    }
    if (!confirm(`¿Desea eliminar al usuario ${usuario.nombreUsuario ?? usuario.correo}?`)) {
      return;
    }
    this.usuarioService.eliminar(usuario.id!).subscribe({
      next: () => this.listarUsuarios(),
      error: (e: HttpErrorResponse) => this.mostrarErrorServidor(e)
    });
  }

  limpiar(): void {
    this.form = this.formularioVacio();
    this.estado = 'ACTIVO';
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

    const exigePassword = !this.editando || f.password.length > 0;
    if (exigePassword && !PASSWORD.test(f.password)) {
      e['password'] = 'Mínimo 8 caracteres, con mayúscula, minúscula y número.';
    }
    if (exigePassword && f.password !== f.confirmarPassword) {
      e['confirmarPassword'] = 'La contraseña y su confirmación no coinciden.';
    }
    return e;
  }

  /** Solo se envían los campos editables; la contraseña, únicamente si se escribió una nueva. */
  private cambios(): UsuarioActualizacion {
    const f = this.form;
    const cambios: UsuarioActualizacion = {
      nombres: f.nombres.trim(),
      apellidos: f.apellidos.trim(),
      correo: f.correo.trim(),
      telefono: f.telefono,
      area: f.area,
      turno: f.turno,
      rolId: f.rolId ?? undefined,
      estado: this.estado
    };
    if (f.password) {
      cambios.password = f.password;
      cambios.confirmarPassword = f.confirmarPassword;
    }
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

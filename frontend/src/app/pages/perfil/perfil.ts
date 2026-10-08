import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Perfil, Usuario, UsuarioActualizacion } from '../../models/usuario';
import { UsuarioService } from '../../services/usuario.service';
import { AuthService } from '../../services/auth.service';
import { mensajeDeError } from '../../core/errores';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';

const SOLO_LETRAS = /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$/;
/** Correo con una sola @, sin espacios y con un punto en el dominio; sin expresiones con retroceso. */
export function esCorreo(valor: string): boolean {
  const partes = valor.split('@');
  if (partes.length !== 2 || /\s/.test(valor)) {
    return false;
  }
  const [usuario, dominio] = partes;
  const punto = dominio.lastIndexOf('.');
  return usuario.length > 0 && punto > 0 && punto < dominio.length - 1;
}
const PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/;

/**
 * HU-34: gestión del perfil propio.
 *
 * El usuario ve sus datos y su actividad de acceso, y edita solo sus datos de
 * contacto. El rol, el área y el turno los asigna el administrador y aquí se
 * muestran como lectura.
 */
@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [DatePipe, FormsModule, EstadoVistaComponent],
  templateUrl: './perfil.html',
  styleUrl: './perfil.css'
})
export class PerfilComponent implements OnInit {

  private readonly usuarioService = inject(UsuarioService);
  private readonly auth = inject(AuthService);

  perfil?: Perfil;
  estado: 'cargando' | 'error' | 'listo' = 'cargando';
  errorCarga = '';

  // Datos de contacto editables
  nombres = '';
  apellidos = '';
  correo = '';
  telefono = '';
  errores: Record<string, string> = {};

  // Contraseña
  password = '';
  confirmarPassword = '';
  mostrarPassword = false;

  guardando = false;
  mensajeError = '';
  mensajeExito = '';

  get usuario(): Usuario | undefined {
    return this.perfil?.usuario;
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.estado = 'cargando';
    this.usuarioService.miPerfil().subscribe({
      next: p => {
        this.perfil = p;
        this.copiar(p.usuario);
        this.estado = 'listo';
      },
      error: (e: HttpErrorResponse) => {
        this.estado = 'error';
        this.errorCarga = mensajeDeError(e, 'Revise la conexión e intente de nuevo.');
      }
    });
  }

  validar(): Record<string, string> {
    const e: Record<string, string> = {};
    if (this.nombres.trim().length < 2 || !SOLO_LETRAS.test(this.nombres.trim())) {
      e['nombres'] = 'Solo letras y espacios; mínimo 2 caracteres.';
    }
    if (this.apellidos.trim() && (this.apellidos.trim().length < 2 || !SOLO_LETRAS.test(this.apellidos.trim()))) {
      e['apellidos'] = 'Solo letras y espacios; mínimo 2 caracteres.';
    }
    if (!esCorreo(this.correo.trim())) {
      e['correo'] = 'Ingrese un correo válido.';
    }
    if (this.telefono && !/^\d{1,15}$/.test(this.telefono)) {
      e['telefono'] = 'Solo dígitos (máximo 15).';
    }
    if (this.password && !PASSWORD.test(this.password)) {
      e['password'] = 'Mínimo 8 caracteres, con mayúscula, minúscula y número.';
    }
    if (this.password !== this.confirmarPassword) {
      e['confirmarPassword'] = 'La contraseña y su confirmación no coinciden.';
    }
    return e;
  }

  guardar(): void {
    this.mensajeError = '';
    this.mensajeExito = '';
    this.errores = this.validar();
    if (Object.keys(this.errores).length > 0) {
      this.mensajeError = 'Revise los campos marcados.';
      return;
    }

    const cambios: UsuarioActualizacion = {
      nombres: this.nombres.trim(),
      apellidos: this.apellidos.trim() || undefined,
      correo: this.correo.trim(),
      telefono: this.telefono
    };
    if (this.password) {
      cambios.password = this.password;
      cambios.confirmarPassword = this.confirmarPassword;
    }

    this.guardando = true;
    this.usuarioService.actualizarMiPerfil(cambios).subscribe({
      next: actualizado => {
        this.guardando = false;
        if (this.perfil) {
          this.perfil = { ...this.perfil, usuario: actualizado };
        }
        this.auth.actualizarUsuario(actualizado);
        this.copiar(actualizado);
        this.password = '';
        this.confirmarPassword = '';
        this.mensajeExito = 'Sus datos se actualizaron.';
      },
      error: (e: HttpErrorResponse) => {
        this.guardando = false;
        this.mensajeError = mensajeDeError(e, 'No se pudo actualizar el perfil.');
      }
    });
  }

  descartar(): void {
    if (this.usuario) {
      this.copiar(this.usuario);
    }
    this.password = '';
    this.confirmarPassword = '';
    this.errores = {};
    this.mensajeError = '';
  }

  iniciales(): string {
    const u = this.usuario;
    if (!u) {
      return '';
    }
    return ((u.nombre?.[0] ?? '') + (u.apellidos?.[0] ?? '')).toUpperCase();
  }

  private copiar(u: Usuario): void {
    this.nombres = u.nombre ?? '';
    this.apellidos = u.apellidos ?? '';
    this.correo = u.correo ?? '';
    this.telefono = u.telefono ?? '';
  }
}

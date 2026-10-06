import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { ErrorApi } from '../../models/respuesta-login';
import { Usuario, UsuarioActualizacion } from '../../models/usuario';
import { UsuarioService } from '../../services/usuario.service';
import { AuthService } from '../../services/auth.service';

/** Edición del perfil propio (HU-07, PATCH /api/usuarios/me). */
@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './perfil.html',
  styleUrl: './perfil.css'
})
export class PerfilComponent implements OnInit {

  private readonly usuarioService = inject(UsuarioService);
  private readonly auth = inject(AuthService);

  usuario: Usuario = { nombre: '', correo: '' };
  password = '';
  confirmarPassword = '';

  mensajeError = '';
  mensajeExito = '';
  mostrarPassword = false;
  mostrarConfirmarPassword = false;

  ngOnInit(): void {
    const guardado = this.auth.usuario;
    if (guardado) {
      this.usuario = { ...guardado };
    }
    this.usuarioService.miPerfil().subscribe(u => (this.usuario = u));
  }

  guardar(): void {
    this.mensajeError = '';
    this.mensajeExito = '';

    if (!this.usuario.nombre?.trim()) {
      this.mensajeError = 'Debe ingresar sus nombres.';
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.usuario.correo ?? '')) {
      this.mensajeError = 'Debe ingresar un correo válido.';
      return;
    }
    if (this.password && !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/.test(this.password)) {
      this.mensajeError = 'La contraseña debe tener al menos 8 caracteres, con mayúscula, minúscula y número.';
      return;
    }
    if (this.password !== this.confirmarPassword) {
      this.mensajeError = 'Las contraseñas no coinciden.';
      return;
    }

    const cambios: UsuarioActualizacion = {
      nombres: this.usuario.nombre.trim(),
      apellidos: this.usuario.apellidos?.trim() || undefined,
      correo: this.usuario.correo.trim(),
      telefono: this.usuario.telefono ?? ''
    };
    if (this.password) {
      cambios.password = this.password;
      cambios.confirmarPassword = this.confirmarPassword;
    }

    this.usuarioService.actualizarMiPerfil(cambios).subscribe({
      next: actualizado => {
        this.usuario = actualizado;
        this.auth.actualizarUsuario(actualizado);
        this.password = '';
        this.confirmarPassword = '';
        this.mensajeExito = 'Perfil actualizado correctamente.';
        setTimeout(() => (this.mensajeExito = ''), 3000);
      },
      error: (e: HttpErrorResponse) => {
        const cuerpo = e.error as ErrorApi | null;
        const detalle = cuerpo?.errores ? Object.values(cuerpo.errores)[0] : undefined;
        this.mensajeError = detalle ?? cuerpo?.mensaje ?? 'No se pudo actualizar el perfil.';
      }
    });
  }

}

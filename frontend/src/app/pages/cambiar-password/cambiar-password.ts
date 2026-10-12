import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from '../../services/auth.service';
import { mensajeDeError } from '../../core/errores';
import { faltas } from '../../core/politica-contrasena';
import { PoliticaContrasenaComponent } from '../../components/politica-contrasena/politica-contrasena';

/**
 * HU-36 y HU-37: cambio obligatorio de la contraseña temporal.
 *
 * Es la única pantalla a la que se puede entrar con una contraseña temporal.
 * La contraseña actual es la temporal que entregó el administrador; la nueva
 * debe cumplir la política, que se va marcando mientras se escribe.
 */
@Component({
  selector: 'app-cambiar-password',
  standalone: true,
  imports: [FormsModule, PoliticaContrasenaComponent],
  templateUrl: './cambiar-password.html',
  styleUrl: './cambiar-password.css'
})
export class CambiarPasswordComponent {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  actual = '';
  nueva = '';
  confirmacion = '';
  mostrar = false;
  enviando = false;
  error = '';

  get nombre(): string {
    return this.auth.usuario?.nombre ?? '';
  }

  get nombreUsuario(): string | null | undefined {
    return this.auth.usuario?.nombreUsuario;
  }

  get documento(): string | null | undefined {
    return this.auth.usuario?.numeroDocumento;
  }

  validar(): string {
    if (!this.actual) {
      return 'Ingrese la contraseña temporal que le entregó el administrador.';
    }
    if (faltas(this.nueva, this.nombreUsuario, this.documento).length > 0) {
      return 'La nueva contraseña no cumple todos los requisitos.';
    }
    if (this.nueva !== this.confirmacion) {
      return 'La nueva contraseña y su confirmación no coinciden.';
    }
    if (this.nueva === this.actual) {
      return 'La nueva contraseña debe ser distinta de la temporal.';
    }
    return '';
  }

  cambiar(): void {
    this.error = this.validar();
    if (this.error) {
      return;
    }
    this.enviando = true;
    this.auth.cambiarPassword(this.actual, this.nueva, this.confirmacion).subscribe({
      next: () => {
        this.enviando = false;
        void this.router.navigate(['/dashboard']);
      },
      error: (e: HttpErrorResponse) => {
        this.enviando = false;
        this.error = mensajeDeError(e, 'No se pudo cambiar la contraseña.');
      }
    });
  }

  salir(): void {
    this.auth.cerrarSesion();
  }
}

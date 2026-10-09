import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { Login } from '../../models/login';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginComponent implements OnInit {

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  loginData: Login = {
    correo: '',
    password: ''
  };

  mensajeError = '';
  mensajeAviso = '';
  enviando = false;
  mostrarPassword = false;

  ngOnInit(): void {
    const motivo = this.ruta.snapshot.queryParamMap.get('motivo');
    if (motivo === 'inactividad') {
      this.mensajeAviso = 'Su sesión se cerró por inactividad. Ingrese nuevamente.';
    } else if (motivo === 'expirada') {
      this.mensajeAviso = 'Su sesión expiró. Ingrese nuevamente.';
    }
  }

  iniciarSesion(): void {

    this.mensajeError = '';

    if (!this.loginData.correo?.trim()) {
      this.mensajeError = 'Debe ingresar su correo o nombre de usuario.';
      return;
    }

    if (!this.loginData.password?.trim()) {
      this.mensajeError = 'Debe ingresar la contraseña.';
      return;
    }

    this.enviando = true;

    this.authService
      .login({ correo: this.loginData.correo.trim(), password: this.loginData.password })
      .subscribe({
        next: respuesta => {
          this.enviando = false;
          // HU-36: con contraseña temporal, lo primero es cambiarla.
          this.router.navigate([respuesta.usuario?.debeCambiarPassword ? '/cambiar-password' : '/dashboard']);
        },
        error: (error: HttpErrorResponse) => {
          this.enviando = false;
          this.mensajeError = mensajeDeLogin(error);
        }
      });
  }
}

/**
 * Mensaje para cada rechazo del inicio de sesión. Las credenciales inválidas
 * reciben siempre el mismo texto, sin decir si falló el usuario o la clave;
 * el bloqueo (HU-35) y el límite de intentos (HU-38) muestran el mensaje del
 * servidor, que dice cuánto esperar.
 */
export function mensajeDeLogin(error: HttpErrorResponse): string {
  switch (error.status) {
    case 401:
      return 'Usuario o contraseña incorrectos.';
    case 423:
    case 429:
      return error.error?.mensaje ?? 'Demasiados intentos. Espere unos minutos e intente nuevamente.';
    default:
      return 'No se pudo conectar con el servidor. Intente nuevamente.';
  }

}

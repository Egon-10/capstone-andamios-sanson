import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { Login } from '../../models/login';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginComponent {

  loginData: Login = {
    correo: '',
    password: ''
  };

  mensajeError = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  iniciarSesion(): void {

  this.mensajeError = '';

  /* Validar correo vacío */
  if (!this.loginData.correo?.trim()) {

    this.mensajeError =
      'Debe ingresar el correo.';

    return;
  }

  /* Validar formato del correo */
  const regexCorreo = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  if (!regexCorreo.test(this.loginData.correo)) {

    this.mensajeError =
      'Debe ingresar un correo válido.';

    return;
  }

  /* Validar contraseña vacía */
  if (!this.loginData.password?.trim()) {

    this.mensajeError =
      'Debe ingresar la contraseña.';

    return;
  }

  /* Si todo está correcto */
  this.authService
    .login(this.loginData)
    .subscribe({

      next: (usuario) => {

        localStorage.setItem(
          'usuario',
          JSON.stringify(usuario)
        );

        this.router.navigate(['/dashboard']);
      },

      error: () => {

        this.mensajeError =
          'Correo o contraseña incorrectos';
      }
    });
}

}
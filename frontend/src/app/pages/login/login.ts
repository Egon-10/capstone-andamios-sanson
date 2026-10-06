import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

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
        next: () => {
          this.enviando = false;
          this.router.navigate(['/dashboard']);
        },
        error: (error: HttpErrorResponse) => {
          this.enviando = false;
          this.mensajeError = error.status === 401
            ? 'Usuario o contraseña incorrectos.'
            : 'No se pudo conectar con el servidor. Intente nuevamente.';
        }
      });
  }

}

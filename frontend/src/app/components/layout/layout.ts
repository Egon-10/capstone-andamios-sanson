import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

import { AuthService } from '../../services/auth.service';
import { SesionInactividadService } from '../../core/sesion-inactividad.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './layout.html',
  styleUrl: './layout.css'
})
export class LayoutComponent implements OnInit, OnDestroy {

  private readonly auth = inject(AuthService);
  readonly inactividad = inject(SesionInactividadService);

  rol = '';
  usuarioNombre = '';
  usuarioRol = '';
  menuAbierto = false;

  ngOnInit(): void {
    const usuario = this.auth.usuario;
    if (usuario) {
      this.rol = usuario.rol?.nombre ?? '';
      this.usuarioNombre = usuario.nombre;
      this.usuarioRol = this.rol;
    }
    this.inactividad.iniciar();
  }

  ngOnDestroy(): void {
    this.inactividad.detener();
  }

  esAdministrador(): boolean {
    return this.rol === 'ADMINISTRADOR';
  }

  esGerente(): boolean {
    return this.rol === 'GERENTE';
  }

  esEncargado(): boolean {
    return this.rol === 'ENCARGADO';
  }

  seguirConectado(): void {
    this.inactividad.continuar();
  }

  logout(): void {
    this.inactividad.detener();
    this.auth.cerrarSesion();
  }

}

import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { SesionInactividadService } from '../../core/sesion-inactividad.service';
import { AlertaService } from '../../services/alerta.service';
import { AlertasComponent } from '../alertas/alertas';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterModule, AlertasComponent],
  templateUrl: './layout.html',
  styleUrl: './layout.css'
})
export class LayoutComponent implements OnInit, OnDestroy {

  private readonly auth = inject(AuthService);
  readonly inactividad = inject(SesionInactividadService);
  private readonly alertas = inject(AlertaService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  rol = '';
  usuarioNombre = '';
  usuarioRol = '';
  menuAbierto = false;
  /** Menú lateral desplegado en pantallas angostas. */
  menuLateralAbierto = false;

  ngOnInit(): void {
    const usuario = this.auth.usuario;
    if (usuario) {
      this.rol = usuario.rol?.nombre ?? '';
      this.usuarioNombre = usuario.nombre;
      this.usuarioRol = this.rol;
    }
    this.inactividad.iniciar();
    this.alertas.iniciar();
    // Al elegir una opción del menú en el celular, el menú se cierra solo.
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.menuLateralAbierto = false;
        this.menuAbierto = false;
      });
  }

  ngOnDestroy(): void {
    this.inactividad.detener();
    this.alertas.detener();
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
    this.alertas.detener();
    this.auth.cerrarSesion();
  }

}

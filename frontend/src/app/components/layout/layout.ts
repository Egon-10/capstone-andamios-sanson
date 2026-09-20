import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './layout.html',
  styleUrl: './layout.css'
})
export class LayoutComponent {

  rol: string = '';
  usuarioNombre = '';
usuarioRol = '';
menuAbierto = false;
  constructor(private router: Router) {

    const usuario = localStorage.getItem('usuario');

    if (usuario) {

      const datos = JSON.parse(usuario);

      this.rol = datos.rol.nombre;
    }
  }

  ngOnInit(): void {

  const usuario =
    localStorage.getItem('usuario');

  if (usuario) {

    const datos =
      JSON.parse(usuario);

    this.usuarioNombre =
      datos.nombre;

    this.usuarioRol =
      datos.rol.nombre;
  }
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

  logout(): void {

    localStorage.removeItem('usuario');

    this.router.navigate(['/login']);
  }

}
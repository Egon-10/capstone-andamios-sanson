import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Usuario } from '../../models/usuario';
import { UsuarioService } from '../../services/usuario.service';

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

  usuario: Usuario = {
    nombre: '',
    correo: '',
    password: '',
    
  };

  confirmarPassword = '';

  mensajeError = '';

  mensajeExito = '';
  mostrarPassword = false;

mostrarConfirmarPassword = false;

  constructor(
    private usuarioService: UsuarioService
  ) {}

  ngOnInit(): void {

    const usuarioGuardado =
      localStorage.getItem('usuario');

    if(usuarioGuardado){

      this.usuario =
        JSON.parse(usuarioGuardado);
    }
  }

  guardar(): void {

    this.mensajeError = '';

    this.mensajeExito = '';

    if(!this.usuario.nombre?.trim()){

      this.mensajeError =
        'Debe ingresar el nombre.';

      return;
    }

    if(!this.usuario.correo?.trim()){

      this.mensajeError =
        'Debe ingresar el correo.';

      return;
    }

    const regexCorreo =
      /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if(!regexCorreo.test(this.usuario.correo)){

      this.mensajeError =
        'Debe ingresar un correo válido.';

      return;
    }

    if(this.usuario.password &&
       this.usuario.password.length < 6){

      this.mensajeError =
        'La contraseña debe tener mínimo 6 caracteres.';

      return;
    }

    if(
      this.usuario.password !==
      this.confirmarPassword
    ){

      this.mensajeError =
        'Las contraseñas no coinciden.';

      return;
    }

    this.usuarioService
      .actualizar(
        this.usuario.id!,
        this.usuario
      )
      .subscribe({

        next: (usuarioActualizado) => {

          localStorage.setItem(
            'usuario',
            JSON.stringify(usuarioActualizado)
          );

          this.mensajeExito =
            'Perfil actualizado correctamente.';

          this.confirmarPassword = '';

          setTimeout(() => {

            this.mensajeExito = '';

          },3000);
        },

        error: () => {

          this.mensajeError =
            'No se pudo actualizar el perfil.';
        }

      });
  }

}
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Usuario } from '../../models/usuario';
import { Rol } from '../../models/rol';

import { UsuarioService } from '../../services/usuario.service';
import { RolService } from '../../services/rol.service';
import { AuditoriaService }
from '../../services/auditoria.service';
@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css'
})
export class UsuariosComponent implements OnInit {

  usuarios: Usuario[] = [];

  roles: Rol[] = [];

  usuario: Usuario = {
    nombre: '',
    correo: '',
    password: ''
  };

  editando = false;
  mensajeError = '';
  mostrarPassword = false;

  constructor(
    private usuarioService: UsuarioService,
    private rolService: RolService,
  private auditoriaService: AuditoriaService
  ) {}

  ngOnInit(): void {

    this.listarUsuarios();

    this.listarRoles();
  }

  listarUsuarios(): void {

    this.usuarioService
      .listar()
      .subscribe(data => {

        this.usuarios = data;
      });
  }

  listarRoles(): void {

    this.rolService
      .listar()
      .subscribe(data => {

        this.roles = data;
      });
  }

  guardar(): void {

  this.mensajeError = '';

  if (!this.usuario.nombre?.trim()) {

    this.mensajeError =
      'Debe ingresar el nombre del usuario.';

    return;
  }

  if (!this.usuario.correo?.trim()) {

    this.mensajeError =
      'Debe ingresar el correo electrónico.';

    return;
  }

  const regexCorreo = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  if (!regexCorreo.test(this.usuario.correo)) {

    this.mensajeError =
      'Debe ingresar un correo válido.';

    return;
  }

  const correoExiste = this.usuarios.some(u =>

    u.correo?.toLowerCase().trim() ===
    this.usuario.correo?.toLowerCase().trim()
    &&
    u.id !== this.usuario.id
  );

  if (correoExiste) {

    this.mensajeError =
      'Ya existe un usuario registrado con ese correo.';

    return;
  }

  if (!this.usuario.password?.trim()) {

    this.mensajeError =
      'Debe ingresar una contraseña.';

    return;
  }

  if (this.usuario.password.length < 6) {

    this.mensajeError =
      'La contraseña debe tener al menos 6 caracteres.';

    return;
  }

  if (!this.usuario.rol) {

    this.mensajeError =
      'Debe seleccionar un rol.';

    return;
  }

  if (this.editando) {

    this.usuarioService
      .actualizar(
        this.usuario.id!,
        this.usuario
      )
      .subscribe(() => {

  this.registrarAuditoria(
    'EDITAR USUARIO: ' +
    this.usuario.nombre
  );

  this.listarUsuarios();

  this.limpiar();
});

  } else {

    this.usuarioService
      .crear(this.usuario)
      .subscribe(() => {

  this.registrarAuditoria(
    'CREAR USUARIO: ' +
    this.usuario.nombre
  );

  this.listarUsuarios();

  this.limpiar();
});
  }
}

  editar(usuario: Usuario): void {

  this.usuario = {
    ...usuario
  };

  this.editando = true;

  this.mensajeError = '';
}

  eliminar(id: number): void {

    if (confirm('¿Desea eliminar este usuario?')) {

      this.usuarioService
        .eliminar(id)
        .subscribe(() => {

  this.registrarAuditoria(
    'ELIMINAR USUARIO ID: ' + id
  );

  this.listarUsuarios();
});
    }
  }

  limpiar(): void {

  this.usuario = {

    nombre: '',
    correo: '',
    password: ''

  };

  this.editando = false;

  this.mensajeError = '';
}

registrarAuditoria(
  accion: string
): void {

  const usuarioGuardado =
    localStorage.getItem('usuario');

  if (!usuarioGuardado) return;

  const usuario =
    JSON.parse(usuarioGuardado);

  const auditoria: any = {

    accion: accion,

    usuario: {
      id: usuario.id
    }

  };

  this.auditoriaService
    .crear(auditoria)
    .subscribe();
}
}
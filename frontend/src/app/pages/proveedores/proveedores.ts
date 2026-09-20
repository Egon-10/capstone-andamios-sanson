import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditoriaService }
from '../../services/auditoria.service';
import { Proveedor } from '../../models/proveedor';
import { ProveedorService } from '../../services/proveedor.service';

@Component({
  selector: 'app-proveedores',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './proveedores.html',
  styleUrl: './proveedores.css'
})
export class ProveedoresComponent implements OnInit {

  proveedores: Proveedor[] = [];

  proveedor: Proveedor = {
    nombre: '',
    ruc: '',
    direccion: '',
    telefono: '',
    correo: ''
  };

  editando = false;
mensajeError = '';
  constructor(
    
    private proveedorService: ProveedorService,
  private auditoriaService: AuditoriaService
  ) {}

  ngOnInit(): void {
    this.listar();
  }

  listar(): void {

    this.proveedorService
      .listar()
      .subscribe(data => {

        this.proveedores = data;
      });
  }

  guardar(): void {

  this.mensajeError = '';

  if (!this.proveedor.nombre?.trim()) {

    this.mensajeError =
      'Debe ingresar el nombre del proveedor.';

    return;
  }

  if (!this.proveedor.ruc?.trim()) {

    this.mensajeError =
      'Debe ingresar el RUC.';

    return;
  }

  if (!/^\d{11}$/.test(this.proveedor.ruc)) {

    this.mensajeError =
      'El RUC debe tener exactamente 11 dígitos.';

    return;
  }

  if (!this.proveedor.direccion?.trim()) {

    this.mensajeError =
      'Debe ingresar la dirección del proveedor.';

    return;
  }

  if (!this.proveedor.telefono?.trim()) {

    this.mensajeError =
      'Debe ingresar el teléfono.';

    return;
  }

  if (!/^\d{9}$/.test(this.proveedor.telefono)) {

    this.mensajeError =
      'El teléfono debe tener exactamente 9 dígitos.';

    return;
  }

  if (!this.proveedor.correo?.trim()) {

    this.mensajeError =
      'Debe ingresar el correo electrónico.';

    return;
  }

  const regexCorreo =
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  if (!regexCorreo.test(this.proveedor.correo)) {

    this.mensajeError =
      'Debe ingresar un correo válido.';

    return;
  }

  const rucExiste =
    this.proveedores.some(p =>

      p.ruc === this.proveedor.ruc &&
      p.id !== this.proveedor.id
    );

  if (rucExiste) {

    this.mensajeError =
      'Ya existe un proveedor registrado con ese RUC.';

    return;
  }

  const correoExiste =
    this.proveedores.some(p =>

      p.correo?.toLowerCase().trim() ===
      this.proveedor.correo?.toLowerCase().trim()
      &&
      p.id !== this.proveedor.id
    );

  if (correoExiste) {

    this.mensajeError =
      'Ya existe un proveedor registrado con ese correo.';

    return;
  }

  if (this.editando) {

    this.proveedorService
      .actualizar(
        this.proveedor.id!,
        this.proveedor
      )
      .subscribe(() => {

        this.registrarAuditoria(
          'EDITÓ el proveedor: ' +
          this.proveedor.nombre
        );

        this.listar();

        this.limpiar();

      });

  } else {

    this.proveedorService
      .crear(this.proveedor)
      .subscribe(() => {

        this.registrarAuditoria(
          'CREÓ el proveedor: ' +
          this.proveedor.nombre
        );

        this.listar();

        this.limpiar();

      });

  }
}

  editar(proveedor: Proveedor): void {

    this.proveedor = {
      ...proveedor
    };

    this.editando = true;
  }

  eliminar(id: number): void {

  const proveedorEliminar =
    this.proveedores.find(
      p => p.id === id
    );

  if (confirm(
    '¿Desea eliminar este proveedor?'
  )) {

    this.proveedorService
      .eliminar(id)
      .subscribe(() => {

        this.registrarAuditoria(
          'ELIMINÓ el proveedor: ' +
          proveedorEliminar?.nombre
        );

        this.listar();

      });
  }
}

  limpiar(): void {

  this.proveedor = {

    nombre: '',
    ruc: '',
    direccion: '',
    telefono: '',
    correo: ''

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

  const auditoria = {

    accion: accion,

    usuario: {
      id: usuario.id
    }

  };

  this.auditoriaService
    .crear(auditoria as any)
    .subscribe();
}
}
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Proveedor } from '../../models/proveedor';
import { ProveedorService } from '../../services/proveedor.service';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';

type EstadoVista = 'cargando' | 'error' | 'vacio' | 'listo';

@Component({
  selector: 'app-proveedores',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    EstadoVistaComponent
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

  /** HU-40: la lista se está consultando, o no se pudo consultar. */
  cargando = true;
  errorCarga = '';

  constructor(
    
    private proveedorService: ProveedorService
  ) {}

  ngOnInit(): void {
    this.listar();
  }

  listar(): void {
    this.cargando = true;
    this.errorCarga = '';

    this.proveedorService
      .listar()
      .subscribe({
        next: data => {
          this.proveedores = data;
          this.cargando = false;
        },
        error: (e: HttpErrorResponse) => {
          this.cargando = false;
          this.errorCarga = e.error?.mensaje || 'Revise su conexión e intente de nuevo.';
        }
      });
  }

  /**
   * HU-40: estado de la lista. Al recargar tras guardar o eliminar se mantiene
   * la tabla visible para que no parpadee ni se pierda el foco.
   */
  get estadoLista(): EstadoVista {
    if (this.errorCarga) {
      return 'error';
    }
    if (this.cargando && this.proveedores.length === 0) {
      return 'cargando';
    }
    return this.proveedores.length === 0 ? 'vacio' : 'listo';
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


        this.listar();

        this.limpiar();

      });

  } else {

    this.proveedorService
      .crear(this.proveedor)
      .subscribe(() => {


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

  if (confirm(
    '¿Desea eliminar este proveedor?'
  )) {

    this.proveedorService
      .eliminar(id)
      .subscribe(() => {


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

// La auditoria del catalogo la registra el servidor (correccion DEF-01).

}
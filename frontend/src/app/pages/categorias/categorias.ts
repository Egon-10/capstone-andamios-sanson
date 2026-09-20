import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditoriaService }
from '../../services/auditoria.service';
import { Categoria } from '../../models/categoria';
import { CategoriaService } from '../../services/categoria.service';

@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './categorias.html',
  styleUrl: './categorias.css'
})
export class CategoriasComponent implements OnInit {

  categorias: Categoria[] = [];

  categoria: Categoria = {
    nombre: ''
  };

  editando = false;
  mensajeError = '';

  constructor(
    private categoriaService: CategoriaService,
    private auditoriaService: AuditoriaService
  ) {}

  ngOnInit(): void {
    this.listar();
  }

  listar(): void {

    this.categoriaService
      .listar()
      .subscribe(data => {

        this.categorias = data;
      });
  }

  guardar(): void {

  this.mensajeError = '';

  if (!this.categoria.nombre?.trim()) {

    this.mensajeError =
      'Debe ingresar el nombre de la categoría.';

    return;
  }

  if (this.editando) {

    this.categoriaService
      .actualizar(
        this.categoria.id!,
        this.categoria
      )
      .subscribe(() => {

    this.registrarAuditoria(
      'EDITÓ la categoría: ' +
      this.categoria.nombre
    );

    this.listar();

    this.limpiar();
});

  } else {

    this.categoriaService
      .crear(this.categoria)
      .subscribe(() => {

    this.registrarAuditoria(
      'CREÓ la categoría: ' +
      this.categoria.nombre
    );

    this.listar();

    this.limpiar();
});
  }
}

  editar(categoria: Categoria): void {

    this.categoria = {
      ...categoria
    };

    this.editando = true;
  }

  eliminar(id: number): void {
    const categoriaEliminar =
  this.categorias.find(
    c => c.id === id
  );

    if (confirm('¿Desea eliminar esta categoría?')) {

      this.categoriaService
        .eliminar(id)
        .subscribe(() => {

    this.registrarAuditoria(
      'ELIMINÓ la categoría: ' +
      categoriaEliminar?.nombre
    );

    this.listar();
});
    }
  }

  limpiar(): void {

  this.categoria = {
    nombre: ''
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
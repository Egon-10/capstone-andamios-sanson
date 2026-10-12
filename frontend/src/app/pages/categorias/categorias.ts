import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
    private categoriaService: CategoriaService
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


    this.listar();

    this.limpiar();
});

  } else {

    this.categoriaService
      .crear(this.categoria)
      .subscribe(() => {


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


    if (confirm('¿Desea eliminar esta categoría?')) {

      this.categoriaService
        .eliminar(id)
        .subscribe(() => {


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
// La auditoria del catalogo la registra el servidor (correccion DEF-01).

}
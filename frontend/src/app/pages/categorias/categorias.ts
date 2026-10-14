import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Categoria } from '../../models/categoria';
import { CategoriaService } from '../../services/categoria.service';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';

type EstadoVista = 'cargando' | 'error' | 'vacio' | 'listo';

@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    EstadoVistaComponent
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

  /** HU-40: la lista se está consultando, o no se pudo consultar. */
  cargando = true;
  errorCarga = '';

  constructor(
    private categoriaService: CategoriaService
  ) {}

  ngOnInit(): void {
    this.listar();
  }

  listar(): void {
    this.cargando = true;
    this.errorCarga = '';

    this.categoriaService
      .listar()
      .subscribe({
        next: data => {
          this.categorias = data;
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
    if (this.cargando && this.categorias.length === 0) {
      return 'cargando';
    }
    return this.categorias.length === 0 ? 'vacio' : 'listo';
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
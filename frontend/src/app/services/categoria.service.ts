import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Categoria } from '../models/categoria';

@Injectable({
  providedIn: 'root'
})
export class CategoriaService {

  private apiUrl =
    'http://localhost:8081/api/categorias';

  constructor(private http: HttpClient) {}

  listar(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(this.apiUrl);
  }

  buscarPorId(id: number): Observable<Categoria> {
    return this.http.get<Categoria>(
      `${this.apiUrl}/${id}`
    );
  }

  crear(categoria: Categoria): Observable<Categoria> {
    return this.http.post<Categoria>(
      this.apiUrl,
      categoria
    );
  }

  actualizar(
    id: number,
    categoria: Categoria
  ): Observable<Categoria> {

    return this.http.put<Categoria>(
      `${this.apiUrl}/${id}`,
      categoria
    );
  }

  eliminar(id: number): Observable<any> {
    return this.http.delete(
      `${this.apiUrl}/${id}`
    );
  }
}
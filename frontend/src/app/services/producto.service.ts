import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Producto } from '../models/producto';
import { ProductoDetalle } from '../models/producto-detalle';

@Injectable({
  providedIn: 'root'
})
export class ProductoService {

  private apiUrl =
    'http://localhost:8081/api/productos';

  constructor(private http: HttpClient) {}

  listar(): Observable<Producto[]> {
    return this.http.get<Producto[]>(this.apiUrl);
  }

  listarDetalle(): Observable<ProductoDetalle[]> {
    return this.http.get<ProductoDetalle[]>(
      `${this.apiUrl}/detalle`
    );
  }

  buscarPorId(id: number): Observable<Producto> {
    return this.http.get<Producto>(
      `${this.apiUrl}/${id}`
    );
  }

  crear(producto: Producto): Observable<Producto> {
    return this.http.post<Producto>(
      this.apiUrl,
      producto
    );
  }

  actualizar(
    id: number,
    producto: Producto
  ): Observable<Producto> {

    return this.http.put<Producto>(
      `${this.apiUrl}/${id}`,
      producto
    );
  }

  eliminar(id: number): Observable<any> {
    return this.http.delete(
      `${this.apiUrl}/${id}`
    );
  }
}
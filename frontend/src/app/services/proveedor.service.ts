import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Proveedor } from '../models/proveedor';

@Injectable({
  providedIn: 'root'
})
export class ProveedorService {

  private apiUrl =
    'http://localhost:8081/api/proveedores';

  constructor(private http: HttpClient) {}

  listar(): Observable<Proveedor[]> {
    return this.http.get<Proveedor[]>(this.apiUrl);
  }

  buscarPorId(id: number): Observable<Proveedor> {
    return this.http.get<Proveedor>(
      `${this.apiUrl}/${id}`
    );
  }

  crear(proveedor: Proveedor): Observable<Proveedor> {
    return this.http.post<Proveedor>(
      this.apiUrl,
      proveedor
    );
  }

  actualizar(
    id: number,
    proveedor: Proveedor
  ): Observable<Proveedor> {

    return this.http.put<Proveedor>(
      `${this.apiUrl}/${id}`,
      proveedor
    );
  }

  eliminar(id: number): Observable<any> {
    return this.http.delete(
      `${this.apiUrl}/${id}`
    );
  }
}
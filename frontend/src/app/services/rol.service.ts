import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Rol } from '../models/rol';

@Injectable({
  providedIn: 'root'
})
export class RolService {

  private apiUrl =
    'http://localhost:8081/api/roles';

  constructor(private http: HttpClient) {}

  listar(): Observable<Rol[]> {
    return this.http.get<Rol[]>(this.apiUrl);
  }

  buscarPorId(id: number): Observable<Rol> {
    return this.http.get<Rol>(
      `${this.apiUrl}/${id}`
    );
  }

  crear(rol: Rol): Observable<Rol> {
    return this.http.post<Rol>(
      this.apiUrl,
      rol
    );
  }

  actualizar(
    id: number,
    rol: Rol
  ): Observable<Rol> {

    return this.http.put<Rol>(
      `${this.apiUrl}/${id}`,
      rol
    );
  }

  eliminar(id: number): Observable<any> {
    return this.http.delete(
      `${this.apiUrl}/${id}`
    );
  }
}
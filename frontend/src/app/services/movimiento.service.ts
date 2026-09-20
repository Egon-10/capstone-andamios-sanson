import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { MovimientoDetalle } from '../models/movimiento-detalle';
import { Movimiento } from '../models/movimiento';

@Injectable({
  providedIn: 'root'
})
export class MovimientoService {

  private apiUrl =
    'http://localhost:8081/api/movimientos';

  constructor(private http: HttpClient) {}

  listar(): Observable<MovimientoDetalle[]> {
    return this.http.get<MovimientoDetalle[]>(
      this.apiUrl
    );
  }

  registrarEntrada(
    productoId: number,
    cantidad: number,
    usuarioId: number
  ): Observable<Movimiento> {

    return this.http.post<Movimiento>(
      `${this.apiUrl}/entrada?productoId=${productoId}&cantidad=${cantidad}&usuarioId=${usuarioId}`,
      {}
    );
  }

  registrarSalida(
    productoId: number,
    cantidad: number,
    usuarioId: number
  ): Observable<Movimiento> {

    return this.http.post<Movimiento>(
      `${this.apiUrl}/salida?productoId=${productoId}&cantidad=${cantidad}&usuarioId=${usuarioId}`,
      {}
    );
  }

  eliminar(id: number) {

  return this.http.delete(
    `${this.apiUrl}/${id}`
  );
}

actualizar(
  id: number,
  cantidad: number
) {

  return this.http.put(
    `${this.apiUrl}/${id}`,
    {
      cantidad: cantidad
    }
  );
}

filtrar(
  fechaInicio?: string,
  fechaFin?: string,
  tipo?: string
): Observable<MovimientoDetalle[]> {

  let url =
    `${this.apiUrl}/filtrar?`;

  if(fechaInicio){
    url += `fechaInicio=${fechaInicio}&`;
  }

  if(fechaFin){
    url += `fechaFin=${fechaFin}&`;
  }

  if(tipo){
    url += `tipo=${tipo}&`;
  }

  return this.http.get<MovimientoDetalle[]>(url);
}
}
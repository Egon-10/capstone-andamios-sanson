import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Motivo } from '../models/motivo';

/**
 * HU-14: catálogo de motivos de movimiento.
 *
 * El formulario lo usa para llenar el desplegable, pero la validación que
 * cuenta es la del servidor: que el motivo exista, esté activo y corresponda
 * al tipo de movimiento no depende de que el cliente haya pedido la lista
 * correcta.
 */
@Injectable({ providedIn: 'root' })
export class MotivoService {

  private apiUrl = `${environment.apiUrl}/motivos-movimiento`;

  constructor(private http: HttpClient) {}

  /** Motivos aplicables a un tipo de movimiento: ENTRADA o SALIDA. */
  listar(tipo?: string): Observable<Motivo[]> {
    let params = new HttpParams();
    if (tipo) {
      params = params.set('tipo', tipo);
    }
    return this.http.get<Motivo[]>(this.apiUrl, { params });
  }
}

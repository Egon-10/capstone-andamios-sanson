import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Ajuste } from '../models/ajuste';

/**
 * HU-16: ajustes de inventario por conteo físico.
 *
 * Registrar el conteo y aprobarlo son dos llamadas distintas a propósito: el
 * conteo no modifica el inventario, y la aprobación la hace otra persona. El
 * servidor restringe aprobación y rechazo al administrador y al gerente, de
 * modo que el encargado que cuenta no puede autorizar su propia diferencia.
 */
@Injectable({ providedIn: 'root' })
export class AjusteService {

  private readonly apiUrl = `${environment.apiUrl}/ajustes`;

  constructor(private readonly http: HttpClient) {}

  listar(estado?: string, productoId?: number): Observable<Ajuste[]> {
    let params = new HttpParams();
    if (estado) {
      params = params.set('estado', estado);
    }
    if (productoId) {
      params = params.set('productoId', productoId);
    }
    return this.http.get<Ajuste[]>(this.apiUrl, { params });
  }

  /** Registra el conteo. El stock no cambia: el ajuste queda pendiente. */
  registrar(productoId: number, stockFisico: number, observacion?: string): Observable<Ajuste> {
    return this.http.post<Ajuste>(this.apiUrl, { productoId, stockFisico, observacion });
  }

  /** Aprueba el conteo y genera el movimiento que corrige el inventario. */
  aprobar(id: number): Observable<Ajuste> {
    return this.http.post<Ajuste>(`${this.apiUrl}/${id}/aprobacion`, {});
  }

  rechazar(id: number, motivo: string): Observable<Ajuste> {
    return this.http.post<Ajuste>(`${this.apiUrl}/${id}/rechazo`, { motivo });
  }
}

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Kardex } from '../models/kardex';
import { Valorizacion, ValorizacionProducto } from '../models/valorizacion';

/**
 * Consultas de inventario: valorización (HU-17), kardex (HU-18) y productos
 * por reponer (HU-20).
 *
 * La valorización está restringida al administrador y al gerente en el
 * servidor, porque es información económica del negocio. El cliente oculta el
 * menú para el encargado, pero la decisión que cuenta es la del servidor.
 */
@Injectable({ providedIn: 'root' })
export class InventarioService {

  private readonly apiUrl = `${environment.apiUrl}/inventario`;

  constructor(private readonly http: HttpClient) {}

  /** HU-17: valor del inventario al costo promedio ponderado. */
  /** HU-17 y HU-28: sin fecha, la valorización vigente; con fecha, al cierre de ese día. */
  valorizacion(fecha?: string | null): Observable<Valorizacion> {
    const params = fecha ? new HttpParams().set('fecha', fecha) : undefined;
    return this.http.get<Valorizacion>(`${this.apiUrl}/valorizacion`, { params });
  }

  /** HU-20: productos que alcanzaron su umbral de reposición. */
  porReponer(): Observable<ValorizacionProducto[]> {
    return this.http.get<ValorizacionProducto[]>(`${this.apiUrl}/por-reponer`);
  }

  /** HU-18: kardex de un producto, opcionalmente acotado por fechas. */
  kardex(productoId: number, desde?: string, hasta?: string): Observable<Kardex> {
    let params = new HttpParams();
    if (desde) {
      params = params.set('desde', desde);
    }
    if (hasta) {
      params = params.set('hasta', hasta);
    }
    return this.http.get<Kardex>(`${this.apiUrl}/kardex/${productoId}`, { params });
  }
}

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Indicadores, StockCritico, Tendencia } from '../models/indicadores';

/** HU-23 a HU-25: panel de indicadores de gestión. */
@Injectable({ providedIn: 'root' })
export class IndicadoresService {

  private readonly apiUrl = `${environment.apiUrl}/indicadores`;

  constructor(private readonly http: HttpClient) {}

  /** HU-23: solo administrador y gerente, porque incluye el valor del inventario. */
  resumen(): Observable<Indicadores> {
    return this.http.get<Indicadores>(`${this.apiUrl}/resumen`);
  }

  /** HU-24: sin fechas, el servidor devuelve los últimos treinta días. */
  tendencia(desde?: string, hasta?: string): Observable<Tendencia> {
    let params = new HttpParams();
    if (desde) {
      params = params.set('desde', desde);
    }
    if (hasta) {
      params = params.set('hasta', hasta);
    }
    return this.http.get<Tendencia>(`${this.apiUrl}/tendencia`, { params });
  }

  /** HU-25: ordenado por el servidor, del más urgente al menos urgente. */
  stockCritico(): Observable<StockCritico[]> {
    return this.http.get<StockCritico[]>(`${this.apiUrl}/stock-critico`);
  }
}

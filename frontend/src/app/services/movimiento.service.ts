import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { MovimientoDetalle } from '../models/movimiento-detalle';

/** Datos que el formulario envía para registrar una entrada o una salida. */
export interface MovimientoSolicitud {
  productoId: number;
  cantidad: number;
  motivo: string;
  observacion?: string;
  costoUnitario?: number;
}

/**
 * Movimientos de inventario.
 *
 * Ya no se envía el identificador del usuario: el servidor lo toma del token
 * de la sesión (CR-04). Tampoco hay métodos de edición ni de borrado, porque
 * un movimiento es inmutable: para corregirlo se anula, y la anulación genera
 * un asiento compensatorio que conserva la traza en el kardex (HU-15).
 */
@Injectable({ providedIn: 'root' })
export class MovimientoService {

  private apiUrl = `${environment.apiUrl}/movimientos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<MovimientoDetalle[]> {
    return this.http.get<MovimientoDetalle[]>(this.apiUrl);
  }

  obtener(id: number): Observable<MovimientoDetalle> {
    return this.http.get<MovimientoDetalle>(`${this.apiUrl}/${id}`);
  }

  /** HU-12: ingreso de mercadería. */
  registrarEntrada(solicitud: MovimientoSolicitud): Observable<MovimientoDetalle> {
    return this.http.post<MovimientoDetalle>(`${this.apiUrl}/entrada`, solicitud);
  }

  /** HU-13: salida de mercadería. */
  registrarSalida(solicitud: MovimientoSolicitud): Observable<MovimientoDetalle> {
    return this.http.post<MovimientoDetalle>(`${this.apiUrl}/salida`, solicitud);
  }

  /**
   * HU-15: anula un movimiento. Devuelve el asiento compensatorio que se creó,
   * no el original, porque es el nuevo asiento el que corrige el inventario.
   */
  anular(id: number, justificacion: string): Observable<MovimientoDetalle> {
    return this.http.post<MovimientoDetalle>(
      `${this.apiUrl}/${id}/anulacion`,
      { justificacion }
    );
  }

  filtrar(fechaInicio?: string, fechaFin?: string, tipo?: string): Observable<MovimientoDetalle[]> {
    let params = new HttpParams();
    if (fechaInicio) {
      params = params.set('fechaInicio', fechaInicio);
    }
    if (fechaFin) {
      params = params.set('fechaFin', fechaFin);
    }
    if (tipo) {
      params = params.set('tipo', tipo);
    }
    return this.http.get<MovimientoDetalle[]>(`${this.apiUrl}/filtrar`, { params });
  }
}

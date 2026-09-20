import { Injectable, inject } from '@angular/core';

import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

import { Dashboard } from '../models/dashboard';
import { MovimientoDetalle } from '../models/movimiento-detalle';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  private http = inject(HttpClient);

  private api = 'http://localhost:8081/api/dashboard';

  obtenerResumen(): Observable<Dashboard> {

    return this.http.get<Dashboard>(
      `${this.api}/resumen`
    );

  }

  obtenerUltimosMovimientos(): Observable<MovimientoDetalle[]> {

    return this.http.get<MovimientoDetalle[]>(
      `${this.api}/ultimos-movimientos`
    );

  }

obtenerProductosPorCategoria(): Observable<any[]> {

  return this.http.get<any[]>(
    `${this.api}/productos-por-categoria`
  );
}

obtenerResumenMovimientos(): Observable<any[]> {

  return this.http.get<any[]>(
    `${this.api}/resumen-movimientos`
  );
}

obtenerProductosMayorStock(): Observable<any[]> {

  return this.http.get<any[]>(
    `${this.api}/productos-mayor-stock`
  );
}
  
obtenerStockCritico() {
  return this.http.get<any[]>(
    `${this.api}/stock-critico`
  );
}


obtenerMovimientosSemana(
  offset:number
){
  return this.http.get<any[]>(
    `${this.api}/movimientos-semana/${offset}`
  );
}
obtenerMovimientosPorDia() {

  return this.http.get<any[]>(
    `${this.api}/movimientos-por-dia`
  );

}
}
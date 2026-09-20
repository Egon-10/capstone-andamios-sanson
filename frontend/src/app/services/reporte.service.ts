import { Injectable } from '@angular/core';

import {
  HttpClient
} from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class ReporteService {

  private apiUrl =
    'http://localhost:8081/api/reportes';

  constructor(
    private http: HttpClient
  ) {}

  descargarMovimientos(
  tipo:string,
  fechaInicio:string,
  fechaFin:string
){

   return this.http.get(
`${this.apiUrl}/movimientos?tipo=${tipo}&fechaInicio=${fechaInicio}&fechaFin=${fechaFin}`,
{
    responseType:'blob'
}
);
  }

  descargarProductos(
  categoria:string
){
  return this.http.get(
    `${this.apiUrl}/productos?categoria=${categoria}`,
    {
      responseType:'blob'
    }
  );
}
descargarStockCritico(
  categoria: string
){
  return this.http.get(
    `${this.apiUrl}/stock-critico?categoria=${categoria}`,
    {
      responseType: 'blob'
    }
  );
}

descargarAuditoria(
    accion:string,
    fechaInicio:string,
    fechaFin:string
){
    return this.http.get(
        `${this.apiUrl}/auditoria?accion=${accion}&fechaInicio=${fechaInicio}&fechaFin=${fechaFin}`,
        {
            responseType:'blob'
        }
    );
}

descargarUsuarios(
    rol:string
){
    return this.http.get(
        `${this.apiUrl}/usuarios?rol=${rol}`,
        {
            responseType:'blob'
        }
    );
}
}
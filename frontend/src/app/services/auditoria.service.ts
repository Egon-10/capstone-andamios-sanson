import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { aParametros } from '../core/parametros';
import { FiltroBitacora, RegistroAcceso, RegistroAuditoria } from '../models/auditoria';
import { Pagina } from '../models/pagina';

/**
 * HU-32 y HU-33: consulta de las bitácoras.
 *
 * Es solo lectura. El cliente ya no registra acciones: la auditoría la
 * escribe el servidor al ejecutar cada operación, para que no se pueda omitir
 * ni fabricar desde el navegador.
 */
@Injectable({ providedIn: 'root' })
export class AuditoriaService {

  private readonly apiUrl = `${environment.apiUrl}`;

  constructor(private readonly http: HttpClient) {}

  /** HU-33: bitácora de acciones con filtros combinados. */
  buscar(filtro: FiltroBitacora): Observable<Pagina<RegistroAuditoria>> {
    return this.http.get<Pagina<RegistroAuditoria>>(`${this.apiUrl}/auditoria`, { params: aParametros(filtro) });
  }

  /** Últimas acciones, para el panel de inicio. */
  ultimos(): Observable<RegistroAuditoria[]> {
    return this.http.get<RegistroAuditoria[]>(`${this.apiUrl}/auditoria/ultimos`);
  }

  /** HU-32: bitácora de accesos (solo administrador). */
  accesos(filtro: FiltroBitacora): Observable<Pagina<RegistroAcceso>> {
    return this.http.get<Pagina<RegistroAcceso>>(`${this.apiUrl}/accesos`, { params: aParametros(filtro) });
  }
}

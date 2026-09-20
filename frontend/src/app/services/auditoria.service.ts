import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Auditoria } from '../models/auditoria';

@Injectable({
  providedIn: 'root'
})
export class AuditoriaService {

  private apiUrl =
    'http://localhost:8081/api/auditoria';

  constructor(private http: HttpClient) {}

  listar(): Observable<Auditoria[]> {
    return this.http.get<Auditoria[]>(
      this.apiUrl
    );
  }

  crear(
    auditoria: Auditoria
  ): Observable<Auditoria> {

    return this.http.post<Auditoria>(
      this.apiUrl,
      auditoria
    );
  }
}
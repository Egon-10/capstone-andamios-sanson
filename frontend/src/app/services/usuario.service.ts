import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { aParametros } from '../core/parametros';
import { UsuarioOpcion } from '../models/auditoria';
import { Pagina } from '../models/pagina';
import { FiltroUsuarios, Perfil, Usuario, UsuarioActualizacion, UsuarioRegistro } from '../models/usuario';

@Injectable({
  providedIn: 'root'
})
export class UsuarioService {

  private apiUrl = `${environment.apiUrl}/usuarios`;

  constructor(private http: HttpClient) {}

  /** HU-30: búsqueda paginada en el servidor. */
  buscar(filtro: FiltroUsuarios): Observable<Pagina<Usuario>> {
    return this.http.get<Pagina<Usuario>>(this.apiUrl, { params: aParametros(filtro) });
  }

  /** Lista corta para los filtros de las bitácoras. */
  opciones(): Observable<UsuarioOpcion[]> {
    return this.http.get<UsuarioOpcion[]>(`${this.apiUrl}/opciones`);
  }

  /** HU-31: activa o desactiva una cuenta. */
  cambiarEstado(id: number, estado: 'ACTIVO' | 'INACTIVO', motivo?: string): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.apiUrl}/${id}/estado`, { estado, motivo: motivo || null });
  }

  buscarPorId(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.apiUrl}/${id}`);
  }

  /** HU-43 */
  registrar(usuario: UsuarioRegistro): Observable<Usuario> {
    return this.http.post<Usuario>(this.apiUrl, usuario);
  }

  /** HU-07: edición parcial mediante PATCH. */
  actualizarParcial(id: number, cambios: UsuarioActualizacion): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.apiUrl}/${id}`, cambios);
  }

  /** HU-34: perfil propio con el acceso anterior. */
  miPerfil(): Observable<Perfil> {
    return this.http.get<Perfil>(`${this.apiUrl}/me`);
  }

  actualizarMiPerfil(cambios: UsuarioActualizacion): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.apiUrl}/me`, cambios);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}

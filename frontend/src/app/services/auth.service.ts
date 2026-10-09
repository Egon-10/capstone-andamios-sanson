import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, finalize, shareReplay, tap, throwError } from 'rxjs';

import { environment } from '../../environments/environment';
import { Login } from '../models/login';
import { RespuestaLogin } from '../models/respuesta-login';
import { Usuario } from '../models/usuario';

const CLAVE_ACCESO = 'accessToken';
const CLAVE_RENOVACION = 'refreshToken';
const CLAVE_USUARIO = 'usuario';
const CLAVE_INACTIVIDAD = 'inactividadMaximaSegundos';

/**
 * HU-03, HU-06, HU-08 y HU-09: gestiona la sesión del cliente
 * (tokens, renovación y cierre de sesión).
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly apiUrl = `${environment.apiUrl}/auth`;
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private renovacionEnCurso$: Observable<RespuestaLogin> | null = null;

  login(datos: Login): Observable<RespuestaLogin> {
    return this.http
      .post<RespuestaLogin>(`${this.apiUrl}/login`, datos)
      .pipe(tap(respuesta => this.guardarSesion(respuesta)));
  }

  /**
   * Renueva la sesión una sola vez aunque varias peticiones la soliciten a la vez:
   * el token de renovación rota en cada uso, así que dos renovaciones paralelas
   * invalidarían la sesión.
   */
  renovar(): Observable<RespuestaLogin> {
    const refreshToken = this.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('No hay una sesión activa'));
    }
    if (!this.renovacionEnCurso$) {
      this.renovacionEnCurso$ = this.http
        .post<RespuestaLogin>(`${this.apiUrl}/refresh`, { refreshToken })
        .pipe(
          tap(respuesta => this.guardarSesion(respuesta)),
          finalize(() => (this.renovacionEnCurso$ = null)),
          shareReplay(1)
        );
    }
    return this.renovacionEnCurso$;
  }

  /**
   * HU-37: cambia la contraseña propia. El servidor cierra las demás sesiones
   * y devuelve una sesión nueva, que reemplaza a la actual.
   */
  cambiarPassword(actual: string, nueva: string, confirmacion: string): Observable<RespuestaLogin> {
    return this.http
      .post<RespuestaLogin>(`${this.apiUrl}/cambio-password`, { actual, nueva, confirmacion })
      .pipe(tap(respuesta => this.guardarSesion(respuesta)));
  }

  /** HU-36: la sesión se abrió con una contraseña temporal que hay que cambiar. */
  get debeCambiarPassword(): boolean {
    return this.usuario?.debeCambiarPassword === true;
  }

  /** Cierra la sesión en el servidor (invalida los tokens) y en el cliente. */
  cerrarSesion(motivo?: 'inactividad' | 'expirada'): void {
    const accessToken = this.accessToken;
    if (accessToken) {
      this.http
        .post(
          `${this.apiUrl}/logout`,
          { refreshToken: this.refreshToken },
          { headers: new HttpHeaders({ Authorization: `Bearer ${accessToken}` }) }
        )
        .subscribe({ error: () => undefined });
    }
    this.limpiarSesion();
    this.router.navigate(['/login'], motivo ? { queryParams: { motivo } } : {});
  }

  /** Cierra la sesión solo en el cliente (cuando el servidor ya la rechazó). */
  cerrarSesionLocal(motivo: 'inactividad' | 'expirada'): void {
    this.limpiarSesion();
    this.router.navigate(['/login'], { queryParams: { motivo } });
  }

  estaAutenticado(): boolean {
    return !!this.accessToken && !!this.usuario;
  }

  get accessToken(): string | null {
    return localStorage.getItem(CLAVE_ACCESO);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(CLAVE_RENOVACION);
  }

  get usuario(): Usuario | null {
    const datos = localStorage.getItem(CLAVE_USUARIO);
    return datos ? (JSON.parse(datos) as Usuario) : null;
  }

  get rol(): string {
    return this.usuario?.rol?.nombre ?? '';
  }

  /** Tiempo máximo sin actividad antes de cerrar la sesión (HU-09). */
  get inactividadMaximaMs(): number {
    const segundos = Number(localStorage.getItem(CLAVE_INACTIVIDAD)) || 1800;
    return segundos * 1000;
  }

  actualizarUsuario(usuario: Usuario): void {
    localStorage.setItem(CLAVE_USUARIO, JSON.stringify(usuario));
  }

  private guardarSesion(respuesta: RespuestaLogin): void {
    localStorage.setItem(CLAVE_ACCESO, respuesta.accessToken);
    localStorage.setItem(CLAVE_RENOVACION, respuesta.refreshToken);
    localStorage.setItem(CLAVE_INACTIVIDAD, String(respuesta.inactividadMaximaSegundos));
    this.actualizarUsuario(respuesta.usuario);
  }

  private limpiarSesion(): void {
    [CLAVE_ACCESO, CLAVE_RENOVACION, CLAVE_USUARIO, CLAVE_INACTIVIDAD].forEach(clave =>
      localStorage.removeItem(clave)
    );
  }
}

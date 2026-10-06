import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth.service';

const RUTAS_PUBLICAS = ['/auth/login', '/auth/refresh', '/auth/logout'];

function conToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/**
 * HU-06: agrega el token a cada petición a la API. Si el servidor responde 401
 * porque el token de acceso venció, renueva la sesión una vez y reintenta.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  const esApi = req.url.startsWith(environment.apiUrl);
  const esPublica = RUTAS_PUBLICAS.some(ruta => req.url.includes(ruta));
  if (!esApi || esPublica) {
    return next(req);
  }

  return next(conToken(req, auth.accessToken)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      if (!auth.refreshToken) {
        auth.cerrarSesionLocal('expirada');
        return throwError(() => error);
      }
      return auth.renovar().pipe(
        switchMap(respuesta => next(conToken(req, respuesta.accessToken))),
        catchError(errorRenovacion => {
          auth.cerrarSesionLocal('expirada');
          return throwError(() => errorRenovacion);
        })
      );
    })
  );
};

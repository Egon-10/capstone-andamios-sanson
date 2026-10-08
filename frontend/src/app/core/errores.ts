import { HttpErrorResponse } from '@angular/common/http';

import { ErrorApi } from '../models/respuesta-login';

/**
 * Mensaje legible para un error del servidor. Prefiere el mensaje que envía
 * la API (ya redactado para el usuario) y, si no hay, uno según el código.
 */
export function mensajeDeError(e: HttpErrorResponse, porOmision: string): string {
  const cuerpo = e.error as ErrorApi | null;
  if (cuerpo && typeof cuerpo === 'object' && 'mensaje' in cuerpo && cuerpo.mensaje) {
    const primero = cuerpo.errores ? Object.values(cuerpo.errores)[0] : undefined;
    return primero ?? cuerpo.mensaje;
  }
  return porCodigo(e.status, porOmision);
}

/**
 * Igual que {@link mensajeDeError}, para las descargas: el cuerpo del error
 * llega como Blob y hay que leerlo antes de interpretarlo.
 */
export async function mensajeDeErrorEnBlob(e: HttpErrorResponse, porOmision: string): Promise<string> {
  if (e.error instanceof Blob) {
    try {
      const cuerpo = JSON.parse(await e.error.text()) as ErrorApi;
      if (cuerpo?.mensaje) {
        return cuerpo.errores ? Object.values(cuerpo.errores)[0] ?? cuerpo.mensaje : cuerpo.mensaje;
      }
    } catch {
      // No era JSON: se usa el mensaje según el código.
    }
  }
  return porCodigo(e.status, porOmision);
}

function porCodigo(estado: number, porOmision: string): string {
  switch (estado) {
    case 0:
      return 'No hay conexión con el servidor. Verifique su red e intente de nuevo.';
    case 403:
      return 'Su rol no tiene permiso para esta acción.';
    case 404:
      return 'El recurso solicitado no existe.';
    case 429:
      return 'Demasiadas solicitudes seguidas. Espere un momento e intente de nuevo.';
    default:
      return porOmision;
  }
}

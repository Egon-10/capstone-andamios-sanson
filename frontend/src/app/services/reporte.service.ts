import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { environment } from '../../environments/environment';
import { aParametros } from '../core/parametros';

/** HU-26 y HU-27: formatos de exportación. */
export type FormatoReporte = 'pdf' | 'xlsx';

/** Reportes que ofrece el servidor; kardex lleva además el producto en la ruta. */
export type TipoReporte =
  | 'movimientos' | 'productos' | 'stock-critico' | 'valorizacion'
  | 'auditoria' | 'accesos' | 'usuarios';

/**
 * HU-26 y HU-27: descarga de reportes en PDF o Excel.
 *
 * El navegador recibe el archivo como blob y lo guarda con el nombre que
 * propone el servidor, que incluye el reporte y la hora de emisión.
 */
@Injectable({ providedIn: 'root' })
export class ReporteService {

  private readonly apiUrl = `${environment.apiUrl}/reportes`;

  constructor(private readonly http: HttpClient) {}

  descargar(tipo: TipoReporte, formato: FormatoReporte, filtros: object = {}): Observable<string> {
    return this.pedir(`${this.apiUrl}/${tipo}`, formato, filtros);
  }

  descargarKardex(productoId: number, formato: FormatoReporte, filtros: object = {}): Observable<string> {
    return this.pedir(`${this.apiUrl}/kardex/${productoId}`, formato, filtros);
  }

  private pedir(url: string, formato: FormatoReporte, filtros: object): Observable<string> {
    const params = aParametros({ ...filtros, formato });
    return this.http.get(url, { params, responseType: 'blob', observe: 'response' }).pipe(
      map(respuesta => {
        const nombre = nombreDeArchivo(respuesta, `reporte.${formato}`);
        this.guardar(respuesta.body as Blob, nombre);
        return nombre;
      })
    );
  }

  /** Guarda el archivo en el equipo. Separado para poder sustituirlo en las pruebas. */
  guardar(contenido: Blob, nombre: string): void {
    const url = URL.createObjectURL(contenido);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    // Se libera en el siguiente ciclo, cuando el navegador ya inició la descarga.
    setTimeout(() => URL.revokeObjectURL(url));
  }
}

/**
 * Lee el nombre del archivo de Content-Disposition. Prefiere filename* (RFC
 * 5987, admite tildes) y si no está usa filename.
 */
export function nombreDeArchivo(respuesta: HttpResponse<unknown>, porOmision: string): string {
  const cabecera = respuesta.headers.get('Content-Disposition') ?? '';
  const extendido = /filename\*=UTF-8''([^;]+)/i.exec(cabecera);
  if (extendido) {
    try {
      return decodeURIComponent(extendido[1].trim());
    } catch {
      // Si viene mal codificado, se intenta con filename.
    }
  }
  const simple = /filename="?([^";]+)"?/i.exec(cabecera);
  return simple ? simple[1].trim() : porOmision;
}

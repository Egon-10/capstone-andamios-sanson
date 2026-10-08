import { HttpParams } from '@angular/common/http';

/**
 * Convierte un objeto de filtros en parámetros de consulta, omitiendo los
 * vacíos. Así un filtro sin completar no viaja como "texto=" o "rolId=null",
 * que el servidor interpretaría como un valor.
 */
export function aParametros(filtros: object): HttpParams {
  let params = new HttpParams();
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor === null || valor === undefined) {
      continue;
    }
    const texto = String(valor).trim();
    if (texto !== '') {
      params = params.set(clave, texto);
    }
  }
  return params;
}

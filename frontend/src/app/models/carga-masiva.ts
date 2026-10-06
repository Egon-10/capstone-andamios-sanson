/** HU-19: resultado de una carga masiva de productos. */
export interface CargaMasiva {
  modo?: string;
  simulacion?: boolean;
  filasLeidas?: number;
  aceptadas?: number;
  rechazadas?: number;
  seGuardo?: boolean;
  errores: FilaRechazada[];
  skusCargados: string[];
}

/** Fila rechazada, con el número que tiene en el archivo. */
export interface FilaRechazada {
  fila?: number;
  sku?: string;
  motivo?: string;
}

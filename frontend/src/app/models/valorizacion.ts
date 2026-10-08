/**
 * HU-17: valorización del inventario al costo promedio ponderado.
 * HU-28: con fechaCorte, la valorización al cierre de ese día.
 */
export interface Valorizacion {
  valorTotal?: number;
  unidadesTotales?: number;
  productosContados?: number;
  porCategoria: ValorizacionCategoria[];
  detalle: ValorizacionProducto[];
  /** Día de corte (AAAA-MM-DD); nulo en la valorización vigente. */
  fechaCorte?: string | null;
  /** Productos cuyo costo al corte se tomó del vigente por falta de historia. */
  productosConCostoEstimado?: number;
}

export interface ValorizacionCategoria {
  categoria?: string;
  productos?: number;
  unidades?: number;
  valor?: number;
  /** Porcentaje que representa la categoría sobre el total. */
  participacion?: number;
}

export interface ValorizacionProducto {
  productoId?: number;
  sku?: string;
  producto?: string;
  categoria?: string;
  stock?: number;
  costoPromedio?: number;
  valor?: number;
  necesitaReposicion?: boolean;
  /** HU-28: el costo al corte no se conoce y se usó el vigente. */
  costoEstimado?: boolean;
}

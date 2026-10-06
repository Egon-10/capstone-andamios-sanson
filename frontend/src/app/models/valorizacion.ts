/** HU-17: valorización del inventario al costo promedio ponderado. */
export interface Valorizacion {
  valorTotal?: number;
  unidadesTotales?: number;
  productosContados?: number;
  porCategoria: ValorizacionCategoria[];
  detalle: ValorizacionProducto[];
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
}

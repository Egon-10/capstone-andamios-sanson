/** HU-21: página de resultados devuelta por el servidor. */
export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  primera: boolean;
  ultima: boolean;
}

/** Producto tal como aparece en una lista o en un resultado de búsqueda. */
export interface ProductoResumen {
  id?: number;
  sku?: string;
  nombre?: string;
  categoria?: string;
  proveedor?: string;
  stock?: number;
  stockMinimo?: number;
  puntoReposicion?: number;
  precio?: number;
  costoPromedio?: number;
  valorizado?: number;
  necesitaReposicion?: boolean;
  activo?: boolean;
}

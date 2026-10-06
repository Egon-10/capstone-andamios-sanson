import { KardexLinea } from './kardex';

/** HU-22: ficha de detalle consolidada de un producto. */
export interface FichaProducto {
  id?: number;
  sku?: string;
  nombre?: string;
  descripcion?: string;
  activo?: boolean;
  categoria?: ReferenciaFicha;
  proveedor?: ReferenciaFicha;
  existencias?: Existencias;
  valorizacion?: ValorizacionFicha;
  resumen?: ResumenFicha;
  ultimosMovimientos: KardexLinea[];
  ajustesPendientes?: number;
}

export interface ReferenciaFicha {
  id?: number;
  nombre?: string;
}

export interface Existencias {
  stock?: number;
  stockMinimo?: number;
  puntoReposicion?: number;
  stockMaximo?: number;
  necesitaReposicion?: boolean;
  faltanteHastaElMaximo?: number;
}

export interface ValorizacionFicha {
  precioVenta?: number;
  costoPromedio?: number;
  valorizado?: number;
}

export interface ResumenFicha {
  movimientos?: number;
  entradas?: number;
  salidas?: number;
  unidadesIngresadas?: number;
  unidadesRetiradas?: number;
  ultimoMovimiento?: string;
}

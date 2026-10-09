/** HU-23: indicadores de gestión del inventario. */
export interface Indicadores {
  productosActivos: number;
  unidadesEnStock: number;
  valorInventario: number;
  productosPorReponer: number;
  productosSinStock: number;
  movimientosHoy: number;
  entradasHoy: number;
  salidasHoy: number;
  ajustesPendientes: number;
  fechaCorte: string;
}

/** HU-24: tendencia diaria de movimientos en un periodo. */
export interface Tendencia {
  desde: string;
  hasta: string;
  totalEntradas: number;
  totalSalidas: number;
  unidadesEntrada: number;
  unidadesSalida: number;
  dias: TendenciaDia[];
}

export interface TendenciaDia {
  fecha: string;
  entradas: number;
  salidas: number;
  unidadesEntrada: number;
  unidadesSalida: number;
}

/** HU-25: producto en situación de stock crítico. */
export interface StockCritico {
  productoId: number;
  sku?: string;
  producto: string;
  categoria: string;
  stock: number;
  umbral: number;
  faltanteHastaUmbral: number;
  reponerHastaMaximo?: number | null;
  nivel: 'AGOTADO' | 'CRITICO' | 'BAJO';
}

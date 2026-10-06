/** HU-18: kardex de un producto con el saldo acumulado por movimiento. */
export interface Kardex {
  productoId?: number;
  sku?: string;
  producto?: string;
  stockActual?: number;
  costoPromedio?: number;
  valorizado?: number;
  /** Saldo anterior al primer asiento: lo que había antes de registrar movimientos. */
  saldoInicial?: number;
  lineas: KardexLinea[];
}

export interface KardexLinea {
  id?: number;
  fecha?: string;
  tipo?: string;
  motivo?: string;
  motivoNombre?: string;
  observacion?: string;
  estado?: string;
  entrada?: number;
  salida?: number;
  costoUnitario?: number;
  valorizado?: number;
  saldo?: number;
  usuario?: string;
}

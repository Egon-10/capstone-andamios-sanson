/**
 * Movimiento tal como lo devuelve la tabla de movimientos del servidor.
 *
 * Incorpora los campos del Sprint 2: el motivo tipificado (HU-14), el estado
 * que distingue un asiento vigente de uno anulado o compensatorio (HU-15) y el
 * saldo resultante del producto (HU-18).
 */
export interface MovimientoDetalle {
  id?: number;
  tipo?: string;
  motivo?: string;
  motivoNombre?: string;
  observacion?: string;
  cantidad?: number;
  fecha?: string;
  estado?: string;
  costoUnitario?: number;
  saldoResultante?: number;
  productoId?: number;
  producto?: string;
  sku?: string;
  usuario?: string;
  movimientoOrigenId?: number;
}

/** HU-16: ajuste de inventario por conteo físico. */
export interface Ajuste {
  id?: number;
  productoId?: number;
  producto?: string;
  sku?: string;
  stockSistema?: number;
  stockFisico?: number;
  /** stockFisico - stockSistema. Positiva si sobra, negativa si falta. */
  diferencia?: number;
  observacion?: string;
  estado?: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';
  usuarioSolicita?: string;
  usuarioAprueba?: string;
  fechaSolicitud?: string;
  fechaResolucion?: string;
  motivoRechazo?: string;
  movimientoId?: number;
}

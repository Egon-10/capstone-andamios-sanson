/** HU-33: registro de la bitácora de auditoría. */
export interface RegistroAuditoria {
  id: number;
  fecha: string;
  accion: string;
  detalle?: string | null;
  usuarioId?: number | null;
  usuario: string;
  nombreUsuario?: string | null;
}

/** HU-32: intento de inicio de sesión. */
export interface RegistroAcceso {
  id: number;
  fecha: string;
  identificador: string;
  usuarioId?: number | null;
  usuario?: string | null;
  resultado: 'EXITOSO' | 'FALLIDO' | 'BLOQUEADO';
  motivo?: string | null;
  ip?: string | null;
  agente?: string | null;
}

/** Filtros comunes de las dos bitácoras. Las fechas van en formato AAAA-MM-DD. */
export interface FiltroBitacora {
  texto?: string;
  usuarioId?: number | null;
  resultado?: string;
  desde?: string;
  hasta?: string;
  pagina?: number;
  tamano?: number;
}

/** Usuario reducido para elegirlo en un filtro. */
export interface UsuarioOpcion {
  id: number;
  nombre: string;
  nombreUsuario?: string | null;
}

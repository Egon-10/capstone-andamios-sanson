import { Usuario } from './usuario';

export interface RespuestaLogin {
  accessToken: string;
  refreshToken: string;
  tipo: string;
  expiraEnSegundos: number;
  inactividadMaximaSegundos: number;
  usuario: Usuario;
}

/** Errores devueltos por la API (GlobalExceptionHandler). */
export interface ErrorApi {
  estado: number;
  mensaje: string;
  errores?: Record<string, string>;
}

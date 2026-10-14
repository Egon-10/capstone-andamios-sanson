import { Rol } from './rol';

/** Usuario tal como lo devuelve la API (nunca incluye la contraseña). */
export interface Usuario {
  id?: number;
  nombre: string;
  apellidos?: string;
  tipoDocumento?: string;
  numeroDocumento?: string;
  correo: string;
  telefono?: string;
  nombreUsuario?: string;
  area?: string;
  turno?: string;
  estado?: string;
  fechaCreacion?: string;
  rol?: Rol;
  /** HU-35: la cuenta está bloqueada por intentos fallidos. */
  bloqueado?: boolean;
  bloqueadoHasta?: string | null;
  /** HU-36: tiene una contraseña temporal que debe cambiar al ingresar. */
  debeCambiarPassword?: boolean;
}

/** HU-36: contraseña temporal generada por el administrador. */
export interface Restablecimiento {
  nombreUsuario: string;
  passwordTemporal: string;
}

/** HU-43: datos del formulario de registro. */
export interface UsuarioRegistro {
  nombres: string;
  apellidos: string;
  tipoDocumento: string;
  numeroDocumento: string;
  correo: string;
  telefono: string;
  nombreUsuario: string;
  password: string;
  confirmarPassword: string;
  rolId: number | null;
  area: string;
  turno: string;
}

/** HU-07: edición parcial; solo se envían los campos que cambian. */
export interface UsuarioActualizacion {
  nombres?: string;
  apellidos?: string;
  correo?: string;
  telefono?: string;
  area?: string;
  turno?: string;
  rolId?: number;
  estado?: string;
  password?: string;
  confirmarPassword?: string;
}

/** HU-30: filtros del listado de usuarios. */
export interface FiltroUsuarios {
  texto?: string;
  rolId?: number | null;
  estado?: string;
  pagina?: number;
  tamano?: number;
  orden?: string;
  descendente?: boolean;
}

/** HU-34: perfil propio con el acceso anterior. */
export interface Perfil {
  usuario: Usuario;
  accesoAnterior?: string | null;
  fallidosDesdeAnterior: number;
}

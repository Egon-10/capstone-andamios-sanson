import { Rol } from './rol';

export interface RespuestaLogin {

  id: number;

  nombre: string;

  correo: string;

  password: string;

  rol: Rol;

}
import { Rol } from './rol';

export interface Usuario {

  id?: number;

  nombre: string;

  correo: string;

  password: string;

  rol?: Rol;

}
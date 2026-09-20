import { Usuario } from './usuario';

export interface Auditoria {

  id?: number;

  accion: string;

  fecha?: string;

  usuario?: Usuario;
  

}
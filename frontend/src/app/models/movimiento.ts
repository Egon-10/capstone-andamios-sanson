import { Producto } from './producto';
import { Usuario } from './usuario';

export interface Movimiento {

  id?: number;

  tipo: string;

  cantidad: number;

  fecha?: string;

  producto?: Producto;

  usuario?: Usuario;

}
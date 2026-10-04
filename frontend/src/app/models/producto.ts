import { Categoria } from './categoria';
import { Proveedor } from './proveedor';

export interface Producto {

  id?: number;

  sku: string;

  nombre: string;

  descripcion: string;

  precio: number;

  stock: number;

  stockMinimo: number;

  categoria?: Categoria;

  proveedor?: Proveedor;

}
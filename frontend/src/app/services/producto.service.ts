import { environment } from '../../environments/environment';
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Producto } from '../models/producto';
import { ProductoDetalle } from '../models/producto-detalle';
import { Pagina, ProductoResumen } from '../models/pagina';
import { FichaProducto } from '../models/ficha-producto';
import { CargaMasiva } from '../models/carga-masiva';

/** HU-21: filtros de la búsqueda paginada. */
export interface FiltrosProducto {
  q?: string;
  categoriaId?: number;
  proveedorId?: number;
  soloActivos?: boolean;
  porReponer?: boolean;
  pagina?: number;
  tamano?: number;
  orden?: string;
  direccion?: 'asc' | 'desc';
}

@Injectable({
  providedIn: 'root'
})
export class ProductoService {

  private readonly apiUrl =
    `${environment.apiUrl}/productos`;

  constructor(private readonly http: HttpClient) {}

  listar(): Observable<Producto[]> {
    return this.http.get<Producto[]>(this.apiUrl);
  }

  listarDetalle(): Observable<ProductoDetalle[]> {
    return this.http.get<ProductoDetalle[]>(
      `${this.apiUrl}/detalle`
    );
  }

  buscarPorId(id: number): Observable<Producto> {
    return this.http.get<Producto>(
      `${this.apiUrl}/${id}`
    );
  }

  crear(producto: Producto): Observable<Producto> {
    return this.http.post<Producto>(
      this.apiUrl,
      producto
    );
  }

  actualizar(
    id: number,
    producto: Producto
  ): Observable<Producto> {

    return this.http.put<Producto>(
      `${this.apiUrl}/${id}`,
      producto
    );
  }

  eliminar(id: number): Observable<any> {
    return this.http.delete(
      `${this.apiUrl}/${id}`
    );
  }

  /**
   * HU-21: búsqueda y filtrado con paginación en el servidor.
   *
   * Reemplaza al listado completo en las pantallas nuevas: devuelve una página
   * y no el catálogo entero, de modo que la búsqueda sigue funcionando cuando
   * el catálogo crece.
   */
  buscar(filtros: FiltrosProducto): Observable<Pagina<ProductoResumen>> {
    let params = new HttpParams();

    if (filtros.q) {
      params = params.set('q', filtros.q);
    }
    if (filtros.categoriaId) {
      params = params.set('categoriaId', filtros.categoriaId);
    }
    if (filtros.proveedorId) {
      params = params.set('proveedorId', filtros.proveedorId);
    }
    if (filtros.soloActivos !== undefined) {
      params = params.set('soloActivos', filtros.soloActivos);
    }
    if (filtros.porReponer !== undefined) {
      params = params.set('porReponer', filtros.porReponer);
    }
    params = params.set('pagina', filtros.pagina ?? 0);
    params = params.set('tamano', filtros.tamano ?? 20);
    params = params.set('orden', filtros.orden ?? 'nombre');
    params = params.set('direccion', filtros.direccion ?? 'asc');

    return this.http.get<Pagina<ProductoResumen>>(`${this.apiUrl}/buscar`, { params });
  }

  /** HU-22: ficha de detalle consolidada del producto. */
  ficha(id: number, ultimosMovimientos = 10): Observable<FichaProducto> {
    const params = new HttpParams().set('ultimosMovimientos', ultimosMovimientos);
    return this.http.get<FichaProducto>(`${this.apiUrl}/${id}/ficha`, { params });
  }

  /** HU-20: umbrales de reposición, que se editan aparte del resto de la ficha. */
  actualizarUmbrales(
    id: number,
    umbrales: { stockMinimo?: number; puntoReposicion?: number; stockMaximo?: number }
  ): Observable<Producto> {
    return this.http.put<Producto>(`${this.apiUrl}/${id}/umbrales`, umbrales);
  }

  /**
   * HU-19: carga masiva desde un archivo CSV.
   *
   * Con simulacion=true el servidor valida el archivo y devuelve el informe sin
   * guardar nada, que es la forma razonable de revisar una planilla larga antes
   * de aplicarla.
   */
  cargaMasiva(archivo: File, modo: string, simulacion: boolean): Observable<CargaMasiva> {
    const cuerpo = new FormData();
    cuerpo.append('archivo', archivo);

    const params = new HttpParams()
      .set('modo', modo)
      .set('simulacion', simulacion);

    return this.http.post<CargaMasiva>(`${this.apiUrl}/carga-masiva`, cuerpo, { params });
  }
}

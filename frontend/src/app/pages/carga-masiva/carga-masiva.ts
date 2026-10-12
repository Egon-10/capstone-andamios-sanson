import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { CargaMasiva } from '../../models/carga-masiva';
import { ProductoService } from '../../services/producto.service';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';

type EstadoVista = EstadoVistaComponent['estado'];

/**
 * HU-19: carga masiva de productos desde un archivo CSV.
 *
 * La pantalla empuja hacia la simulacion antes de la carga real, porque es lo
 * que evita el problema tipico: aplicar una planilla de cientos de filas y
 * descubrir despues que la mitad tenia la categoria mal escrita. La simulacion
 * devuelve el mismo informe sin guardar nada.
 */
@Component({
  selector: 'app-carga-masiva',
  standalone: true,
  imports: [CommonModule, FormsModule, EstadoVistaComponent],
  templateUrl: './carga-masiva.html',
  styleUrl: './carga-masiva.css'
})
export class CargaMasivaComponent {

  archivo?: File;
  nombreArchivo = '';
  modo: 'TODO_O_NADA' | 'PARCIAL' = 'TODO_O_NADA';

  informe?: CargaMasiva;
  procesando = false;
  mensajeError = '';

  /**
   * HU-40: error del servidor al procesar el archivo. Se separa de
   * mensajeError, que tambien cubre lo que falta en el formulario, para
   * ofrecer reintentar solo cuando hay algo que repetir.
   */
  errorCarga = '';
  private ultimoEnvioFueSimulacion = true;

  constructor(private readonly productoService: ProductoService) {}

  seleccionar(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivos = entrada.files;

    this.informe = undefined;
    this.mensajeError = '';
    this.errorCarga = '';

    if (!archivos || archivos.length === 0) {
      this.archivo = undefined;
      this.nombreArchivo = '';
      return;
    }

    this.archivo = archivos[0];
    this.nombreArchivo = this.archivo.name;
  }

  simular(): void {
    this.enviar(true);
  }

  cargar(): void {
    this.enviar(false);
  }

  /** Repite el ultimo envio (simulacion o carga) tras un error del servidor. */
  reintentar(): void {
    this.enviar(this.ultimoEnvioFueSimulacion);
  }

  /** HU-40: estado del procesamiento del archivo. */
  get estadoVista(): EstadoVista {
    if (this.procesando) {
      return 'cargando';
    }
    if (this.errorCarga) {
      return 'error';
    }
    if (this.informe && !this.informe.filasLeidas && this.informe.errores.length === 0) {
      return 'vacio';
    }
    return 'listo';
  }

  private enviar(simulacion: boolean): void {
    this.mensajeError = '';
    this.errorCarga = '';
    this.ultimoEnvioFueSimulacion = simulacion;

    if (!this.archivo) {
      this.mensajeError = 'Seleccione el archivo CSV con los productos';
      return;
    }

    this.procesando = true;
    this.productoService.cargaMasiva(this.archivo, this.modo, simulacion).subscribe({
      next: informe => {
        this.procesando = false;
        this.informe = informe;
      },
      error: (e: HttpErrorResponse) => {
        this.procesando = false;
        this.informe = undefined;
        this.mensajeError = e.error?.mensaje || 'No se pudo procesar el archivo';
        this.errorCarga = this.mensajeError;
      }
    });
  }

  /**
   * Genera una planilla de ejemplo con las columnas que espera el servidor.
   * Es la forma mas corta de explicar el formato: en lugar de documentarlo,
   * se entrega un archivo que ya funciona y se edita encima.
   */
  descargarPlantilla(): void {
    const cabecera = 'sku;nombre;descripcion;precio;stock;'
      + 'stockMinimo;puntoReposicion;stockMaximo;categoria;proveedor';

    const ejemplo = 'AND-100;Marco de andamio 1.50 m;Galvanizado;300;50;10;15;200;'
      + 'ANDAMIOS ACRO;';

    const csv = cabecera + '\n' + ejemplo + '\n';

    // La marca de orden de bytes hace que Excel reconozca el UTF-8.
    const contenido = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(contenido);

    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = 'plantilla-carga-productos.csv';
    enlace.click();

    URL.revokeObjectURL(url);
  }

  limpiar(): void {
    this.archivo = undefined;
    this.nombreArchivo = '';
    this.informe = undefined;
    this.mensajeError = '';
    this.errorCarga = '';
  }
}

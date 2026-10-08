import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { IndicadoresService } from '../../services/indicadores.service';
import { Indicadores, StockCritico, Tendencia, TendenciaDia } from '../../models/indicadores';

/** Barra del gráfico ya convertida a coordenadas del SVG. */
interface Barra {
  x: number;
  y: number;
  ancho: number;
  alto: number;
  trazo: string;
}

interface GrupoDia {
  dia: TendenciaDia;
  entrada: Barra;
  salida: Barra;
  hitX: number;
  hitAncho: number;
  etiqueta?: string;
  etiquetaX: number;
}

type Medida = 'movimientos' | 'unidades';

/**
 * HU-23 a HU-25: panel de indicadores de gestión, que encabeza el dashboard.
 *
 * El gráfico de tendencia se dibuja en SVG y no con una librería de gráficos
 * porque tiene que cumplir reglas que una librería no garantiza por omisión:
 * una sola escala, barras con remate redondeado anclado a la línea base, un
 * hueco de 2 px entre barras vecinas, y el mismo detalle con el teclado que con
 * el mouse. Los datos también se pueden ver como tabla, de modo que ningún
 * valor depende de pasar el puntero.
 */
@Component({
  selector: 'app-panel-indicadores',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './panel-indicadores.html',
  styleUrl: './panel-indicadores.css'
})
export class PanelIndicadoresComponent implements OnInit {

  // Geometría del gráfico, en unidades del viewBox.
  readonly ancho = 720;
  readonly alto = 220;
  readonly margenIzq = 44;
  readonly margenDer = 8;
  readonly margenSup = 12;
  readonly margenInf = 26;
  private readonly hueco = 2;

  rol = '';
  indicadores?: Indicadores;
  tendencia?: Tendencia;
  stockCritico: StockCritico[] = [];

  periodo = 30;
  medida: Medida = 'movimientos';
  verTabla = false;
  diaActivo?: number;

  error = '';
  errorTendencia = '';

  grupos: GrupoDia[] = [];
  marcasY: { valor: number; y: number }[] = [];

  constructor(private readonly servicio: IndicadoresService) {}

  ngOnInit(): void {
    this.rol = this.leerRol();
    if (this.verResumen) {
      this.servicio.resumen().subscribe({
        next: i => (this.indicadores = i),
        error: (e: HttpErrorResponse) => (this.error = this.mensaje(e, 'No se pudieron cargar los indicadores'))
      });
    }
    this.servicio.stockCritico().subscribe({
      next: s => (this.stockCritico = s),
      error: (e: HttpErrorResponse) => (this.error = this.mensaje(e, 'No se pudo cargar el stock crítico'))
    });
    this.cargarTendencia();
  }

  /** El resumen incluye el valor del inventario: solo administrador y gerente. */
  get verResumen(): boolean {
    return this.rol === 'ADMINISTRADOR' || this.rol === 'GERENTE';
  }

  cambiarPeriodo(dias: number): void {
    this.periodo = dias;
    this.cargarTendencia();
  }

  cambiarMedida(medida: Medida): void {
    this.medida = medida;
    this.dibujar();
  }

  cargarTendencia(): void {
    this.errorTendencia = '';
    const hasta = new Date();
    const desde = new Date(hasta);
    desde.setDate(hasta.getDate() - (this.periodo - 1));

    this.servicio.tendencia(this.comoFecha(desde), this.comoFecha(hasta)).subscribe({
      next: t => {
        this.tendencia = t;
        this.diaActivo = undefined;
        this.dibujar();
      },
      error: (e: HttpErrorResponse) =>
        (this.errorTendencia = this.mensaje(e, 'No se pudo cargar la tendencia'))
    });
  }

  /** Valor del día según la medida elegida. */
  valorEntrada(d: TendenciaDia): number {
    return this.medida === 'unidades' ? d.unidadesEntrada : d.entradas;
  }

  valorSalida(d: TendenciaDia): number {
    return this.medida === 'unidades' ? d.unidadesSalida : d.salidas;
  }

  get sinMovimientos(): boolean {
    return !!this.tendencia && this.tendencia.dias.every(d => d.entradas === 0 && d.salidas === 0);
  }

  get grupoActivo(): GrupoDia | undefined {
    return this.diaActivo === undefined ? undefined : this.grupos[this.diaActivo];
  }

  /** Posición horizontal del tooltip, en porcentaje del ancho del gráfico. */
  get tooltipIzquierda(): number {
    const g = this.grupoActivo;
    if (!g) {
      return 0;
    }
    return ((g.hitX + g.hitAncho / 2) / this.ancho) * 100;
  }

  activar(indice: number): void {
    this.diaActivo = indice;
  }

  desactivar(): void {
    this.diaActivo = undefined;
  }

  /** Convierte la serie en barras. Se llama al cargar y al cambiar la medida. */
  dibujar(): void {
    const dias = this.tendencia?.dias ?? [];
    const anchoPlot = this.ancho - this.margenIzq - this.margenDer;
    const altoPlot = this.alto - this.margenSup - this.margenInf;
    const base = this.margenSup + altoPlot;

    const maximo = Math.max(1, ...dias.map(d => Math.max(this.valorEntrada(d), this.valorSalida(d))));
    const tope = this.topeRedondo(maximo);

    this.marcasY = [0, 0.25, 0.5, 0.75, 1].map(f => ({
      valor: Math.round(tope * f),
      y: base - altoPlot * f
    }));

    const n = Math.max(dias.length, 1);
    const anchoGrupo = anchoPlot / n;
    // Dos barras por día con un hueco de 2 px entre ellas y otro entre días.
    const anchoBarra = Math.max(1, (anchoGrupo - this.hueco * 2) / 2);
    const cadaCuantos = Math.ceil(n / 8);

    this.grupos = dias.map((dia, i) => {
      const x0 = this.margenIzq + i * anchoGrupo + this.hueco / 2;
      const altoE = (this.valorEntrada(dia) / tope) * altoPlot;
      const altoS = (this.valorSalida(dia) / tope) * altoPlot;
      return {
        dia,
        entrada: this.barra(x0, base, anchoBarra, altoE),
        salida: this.barra(x0 + anchoBarra + this.hueco, base, anchoBarra, altoS),
        hitX: this.margenIzq + i * anchoGrupo,
        hitAncho: anchoGrupo,
        etiqueta: i % cadaCuantos === 0 ? this.etiquetaDia(dia.fecha) : undefined,
        etiquetaX: this.margenIzq + i * anchoGrupo + anchoGrupo / 2
      };
    });
  }

  /**
   * Barra con las esquinas superiores redondeadas y la base recta, para que
   * se lea anclada al eje. Un valor cero no se dibuja.
   */
  barra(x: number, base: number, ancho: number, alto: number): Barra {
    const y = base - alto;
    if (alto <= 0) {
      return { x, y: base, ancho, alto: 0, trazo: '' };
    }
    const r = Math.min(4, ancho / 2, alto);
    const trazo = `M${x},${base} V${y + r} Q${x},${y} ${x + r},${y} `
      + `H${x + ancho - r} Q${x + ancho},${y} ${x + ancho},${y + r} V${base} Z`;
    return { x, y, ancho, alto, trazo };
  }

  /** Tope del eje: el siguiente múltiplo "redondo" del máximo. */
  topeRedondo(maximo: number): number {
    if (maximo <= 4) {
      return 4;
    }
    const potencia = Math.pow(10, Math.floor(Math.log10(maximo)));
    for (const paso of [1, 2, 2.5, 5, 10]) {
      const tope = paso * potencia;
      if (tope >= maximo) {
        return tope;
      }
    }
    return 10 * potencia;
  }

  etiquetaDia(fecha: string): string {
    const [, mes, dia] = fecha.split('-');
    return `${dia}/${mes}`;
  }

  /** Ícono que acompaña al nivel: el color nunca carga el significado solo. */
  icono(nivel: string): string {
    if (nivel === 'AGOTADO') {
      return 'fa-circle-xmark';
    }
    return nivel === 'CRITICO' ? 'fa-triangle-exclamation' : 'fa-circle-exclamation';
  }

  /** Antepone la cantidad y elige singular o plural: 1 entrada, 2 entradas. */
  conUnidad(cantidad: number, singular: string, plural: string): string {
    return `${cantidad} ${cantidad === 1 ? singular : plural}`;
  }

  etiquetaNivel(nivel: string): string {
    if (nivel === 'AGOTADO') {
      return 'Agotado';
    }
    return nivel === 'CRITICO' ? 'Crítico' : 'Bajo';
  }

  private comoFecha(d: Date): string {
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mes}-${dia}`;
  }

  private leerRol(): string {
    const guardado = localStorage.getItem('usuario');
    if (!guardado) {
      return '';
    }
    try {
      return JSON.parse(guardado)?.rol?.nombre ?? '';
    } catch {
      return '';
    }
  }

  private mensaje(e: HttpErrorResponse, porOmision: string): string {
    return e.error?.mensaje || porOmision;
  }
}

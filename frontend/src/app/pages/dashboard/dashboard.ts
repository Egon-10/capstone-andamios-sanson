import { Component, DestroyRef, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription } from 'rxjs';
import {
  ArcElement, BarController, BarElement, CategoryScale, Chart, ChartConfiguration,
  Legend, LinearScale, PieController, Tooltip
} from 'chart.js';

import { PanelIndicadoresComponent } from '../../components/panel-indicadores/panel-indicadores';
import { EstadoVistaComponent } from '../../components/estado-vista/estado-vista';
import { DashboardService } from '../../services/dashboard.service';
import { AuthService } from '../../services/auth.service';
import { Dashboard } from '../../models/dashboard';
import { MovimientoDetalle } from '../../models/movimiento-detalle';

Chart.register(BarController, BarElement, CategoryScale, LinearScale, PieController, ArcElement, Legend, Tooltip);

type Estado = 'cargando' | 'error' | 'vacio' | 'listo';
type NombreGrafica = 'categorias' | 'movimientos' | 'stock' | 'critico' | 'semana';

/** Filas que devuelve el servidor para las gráficas (List<Object[]>). */
type FilaValor = [string, number];
type FilaCritico = [string, number, number];
type FilaSemana = [number, string, number];

/** Colores de las gráficas: se definen una vez y coinciden con los tokens de estado. */
export const COLORES_GRAFICAS = {
  serie: ['#2563eb', '#15803d', '#d97706', '#b91c1c', '#7c3aed', '#0891b2', '#db2777', '#475569'],
  entrada: '#15803d',
  salida: '#b91c1c',
  minimo: '#d97706',
  borde: '#ffffff'
} as const;

const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

/** Escala Y en unidades enteras, empezando en cero. */
const ESCALA_ENTERA = { y: { beginAtZero: true, ticks: { precision: 0 } } };

/** Tarjetas del resumen, en el orden en que se muestran. */
const TARJETAS: { clave: keyof Dashboard; titulo: string; icono: string; tono: string }[] = [
  { clave: 'totalProductos', titulo: 'Productos', icono: 'fa-box', tono: 'info' },
  { clave: 'totalCategorias', titulo: 'Categorías', icono: 'fa-tags', tono: 'primario' },
  { clave: 'totalProveedores', titulo: 'Proveedores', icono: 'fa-truck', tono: 'aviso' },
  { clave: 'totalUsuarios', titulo: 'Usuarios', icono: 'fa-users', tono: 'exito' },
  { clave: 'stockTotal', titulo: 'Stock total', icono: 'fa-cubes', tono: 'peligro' }
];

/**
 * Pantalla de inicio: resumen general, indicadores de gestión, gráficas y los
 * últimos movimientos. El acceso ya lo controla el guard de autenticación.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, PanelIndicadoresComponent, EstadoVistaComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnInit, OnDestroy {

  private readonly servicio = inject(DashboardService);
  private readonly destruir = inject(DestroyRef);

  // Los lienzos están siempre en la plantilla (fuera de @if), así que existen desde ngOnInit.
  @ViewChild('lienzoCategorias', { static: true }) private lienzoCategorias!: ElementRef<HTMLCanvasElement>;
  @ViewChild('lienzoMovimientos', { static: true }) private lienzoMovimientos!: ElementRef<HTMLCanvasElement>;
  @ViewChild('lienzoStock', { static: true }) private lienzoStock!: ElementRef<HTMLCanvasElement>;
  @ViewChild('lienzoCritico', { static: true }) private lienzoCritico!: ElementRef<HTMLCanvasElement>;
  @ViewChild('lienzoSemana', { static: true }) private lienzoSemana!: ElementRef<HTMLCanvasElement>;

  readonly nombreUsuario = inject(AuthService).usuario?.nombre || 'Usuario';
  readonly hoy = new Date();
  readonly tarjetas = TARJETAS;

  resumen: Dashboard | null = null;
  estadoResumen: Estado = 'cargando';

  movimientos: MovimientoDetalle[] = [];
  movimientosFiltrados: MovimientoDetalle[] = [];
  estadoMovimientos: Estado = 'cargando';
  filtro = { desde: '', hasta: '', tipo: '' };

  /** Gráficas cuya consulta falló. */
  erroresGrafica: Partial<Record<NombreGrafica, boolean>> = {};

  semana = 0;
  inicioSemana = new Date();
  finSemana = new Date();

  private readonly graficas = new Map<NombreGrafica, Chart>();
  private consultaSemana?: Subscription;

  ngOnInit(): void {
    this.cargarResumen();
    this.cargarMovimientos();
    this.cargarGraficas();
    this.cargarSemana();
  }

  ngOnDestroy(): void {
    this.consultaSemana?.unsubscribe();
    this.graficas.forEach(g => g.destroy());
    this.graficas.clear();
  }

  cargarResumen(): void {
    this.estadoResumen = 'cargando';
    this.servicio.obtenerResumen().pipe(takeUntilDestroyed(this.destruir)).subscribe({
      next: resumen => {
        this.resumen = resumen;
        this.estadoResumen = resumen ? 'listo' : 'vacio';
      },
      error: () => (this.estadoResumen = 'error')
    });
  }

  cargarMovimientos(): void {
    this.estadoMovimientos = 'cargando';
    this.servicio.obtenerUltimosMovimientos().pipe(takeUntilDestroyed(this.destruir)).subscribe({
      next: movimientos => {
        this.movimientos = movimientos ?? [];
        this.estadoMovimientos = 'listo';
        this.aplicarFiltros();
      },
      error: () => (this.estadoMovimientos = 'error')
    });
  }

  /** Estado de la tabla: distingue "no cargó" de "no hay datos". */
  get estadoTabla(): Estado {
    if (this.estadoMovimientos !== 'listo') {
      return this.estadoMovimientos;
    }
    return this.movimientosFiltrados.length === 0 ? 'vacio' : 'listo';
  }

  get hayFiltros(): boolean {
    return !!(this.filtro.desde || this.filtro.hasta || this.filtro.tipo);
  }

  get rangoInvertido(): boolean {
    return !!this.filtro.desde && !!this.filtro.hasta && this.filtro.desde > this.filtro.hasta;
  }

  aplicarFiltros(): void {
    // Las fechas del filtro son locales: sin la hora, Date las tomaría como UTC.
    const desde = this.filtro.desde ? new Date(`${this.filtro.desde}T00:00:00`) : null;
    const hasta = this.filtro.hasta ? new Date(`${this.filtro.hasta}T23:59:59.999`) : null;
    const tipo = this.filtro.tipo;

    this.movimientosFiltrados = this.movimientos.filter(m => {
      const fecha = m.fecha ? new Date(m.fecha) : null;
      return (!tipo || m.tipo === tipo)
        && (!desde || (!!fecha && fecha >= desde))
        && (!hasta || (!!fecha && fecha <= hasta));
    });
  }

  limpiarFiltros(): void {
    this.filtro = { desde: '', hasta: '', tipo: '' };
    this.aplicarFiltros();
  }

  insignia(tipo?: string): string {
    if (tipo === 'ENTRADA') {
      return 'exito';
    }
    return tipo === 'SALIDA' ? 'peligro' : '';
  }

  // ------------------------------------------------------------------
  // Gráficas
  // ------------------------------------------------------------------

  cambiarSemana(desplazamiento: number): void {
    this.semana = desplazamiento === 0 ? 0 : this.semana + desplazamiento;
    this.cargarSemana();
  }

  private cargarGraficas(): void {
    this.consultar('categorias', this.servicio.obtenerProductosPorCategoria(), filas =>
      this.dibujarTorta('categorias', filas as FilaValor[]));

    this.consultar('movimientos', this.servicio.obtenerResumenMovimientos(), filas =>
      this.dibujarTorta('movimientos', filas as FilaValor[]));

    this.consultar('stock', this.servicio.obtenerProductosMayorStock(), filas => {
      const top = (filas as FilaValor[]).slice(0, 5);
      this.dibujar('stock', {
        type: 'bar',
        data: {
          labels: top.map(f => f[0]),
          datasets: [{ label: 'Cantidad en stock', data: top.map(f => Number(f[1])), backgroundColor: [...COLORES_GRAFICAS.serie] }]
        },
        options: { scales: ESCALA_ENTERA, plugins: { legend: { display: false } } }
      });
    });

    this.consultar('critico', this.servicio.obtenerStockCritico(), filas => {
      const datos = filas as FilaCritico[];
      this.dibujar('critico', {
        type: 'bar',
        data: {
          labels: datos.map(f => f[0]),
          datasets: [
            { label: 'Stock actual', data: datos.map(f => Number(f[1])), backgroundColor: COLORES_GRAFICAS.salida },
            { label: 'Stock mínimo', data: datos.map(f => Number(f[2])), backgroundColor: COLORES_GRAFICAS.minimo }
          ]
        },
        options: { scales: ESCALA_ENTERA }
      });
    });
  }

  private cargarSemana(): void {
    // La semana va de lunes a domingo, igual que YEARWEEK(fecha, 1) en el servidor.
    const lunes = new Date();
    lunes.setHours(0, 0, 0, 0);
    lunes.setDate(lunes.getDate() - ((lunes.getDay() + 6) % 7) + this.semana * 7);
    const domingo = new Date(lunes);
    domingo.setDate(lunes.getDate() + 6);
    this.inicioSemana = lunes;
    this.finSemana = domingo;

    // Si se cambia de semana rápido, la respuesta anterior ya no interesa.
    this.consultaSemana?.unsubscribe();
    this.consultaSemana = this.consultar('semana', this.servicio.obtenerMovimientosSemana(this.semana), filas => {
      const entradas = Array(7).fill(0);
      const salidas = Array(7).fill(0);
      for (const [diaSql, tipo, total] of filas as FilaSemana[]) {
        // DAYOFWEEK: 1 = domingo … 7 = sábado. Se pasa a 0 = lunes … 6 = domingo.
        const dia = (Number(diaSql) + 5) % 7;
        if (tipo === 'ENTRADA') {
          entradas[dia] = Number(total);
        } else if (tipo === 'SALIDA') {
          salidas[dia] = Number(total);
        }
      }
      this.dibujar('semana', {
        type: 'bar',
        data: {
          labels: DIAS,
          datasets: [
            { label: 'Entradas', data: entradas, backgroundColor: COLORES_GRAFICAS.entrada, borderRadius: 4 },
            { label: 'Salidas', data: salidas, backgroundColor: COLORES_GRAFICAS.salida, borderRadius: 4 }
          ]
        },
        options: { scales: ESCALA_ENTERA }
      });
    });
  }

  private consultar(nombre: NombreGrafica, origen: Observable<unknown[]>, dibujar: (filas: unknown[]) => void): Subscription {
    this.erroresGrafica = { ...this.erroresGrafica, [nombre]: false };
    return origen.pipe(takeUntilDestroyed(this.destruir)).subscribe({
      next: filas => dibujar(filas ?? []),
      error: () => {
        this.graficas.get(nombre)?.destroy();
        this.graficas.delete(nombre);
        this.erroresGrafica = { ...this.erroresGrafica, [nombre]: true };
      }
    });
  }

  private dibujarTorta(nombre: NombreGrafica, filas: FilaValor[]): void {
    this.dibujar(nombre, {
      type: 'pie',
      data: {
        labels: filas.map(f => f[0]),
        datasets: [{
          data: filas.map(f => Number(f[1])),
          backgroundColor: [...COLORES_GRAFICAS.serie],
          borderColor: COLORES_GRAFICAS.borde,
          borderWidth: 2
        }]
      }
    });
  }

  /** Crea (o reemplaza) la gráfica en su lienzo. */
  private dibujar(nombre: NombreGrafica, config: ChartConfiguration): void {
    this.graficas.get(nombre)?.destroy();
    const lienzo = this.lienzo(nombre).nativeElement;
    config.options = { responsive: true, maintainAspectRatio: false, ...config.options };
    this.graficas.set(nombre, new Chart(lienzo, config));
  }

  private lienzo(nombre: NombreGrafica): ElementRef<HTMLCanvasElement> {
    switch (nombre) {
      case 'categorias': return this.lienzoCategorias;
      case 'movimientos': return this.lienzoMovimientos;
      case 'stock': return this.lienzoStock;
      case 'critico': return this.lienzoCritico;
      case 'semana': return this.lienzoSemana;
    }
  }
}

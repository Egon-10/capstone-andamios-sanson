import {
  Component,
  OnInit,
  inject
} from '@angular/core';

import {
  CommonModule
} from '@angular/common';

import {
  DashboardService
} from '../../services/dashboard.service';

import {
  Dashboard
} from '../../models/dashboard';
import { FormsModule } from '@angular/forms';
import {
  MovimientoDetalle
} from '../../models/movimiento-detalle';

import {
  Chart,
  BarController,
  BarElement,
  CategoryScale,
  LinearScale,
  PieController,
  ArcElement,
  LineController,
  LineElement,
  PointElement,
  Legend,
  Tooltip,
  Title
} from 'chart.js';

Chart.register(
  BarController,
  BarElement,
  CategoryScale,
  LinearScale,

  PieController,
  ArcElement,

  LineController,
  LineElement,
  PointElement,

  Legend,
  Tooltip,
  Title
);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
  CommonModule,
  FormsModule
],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class DashboardComponent
implements OnInit {

  private dashboardService =
    inject(DashboardService);

  resumen!: Dashboard;

  movimientos:
    MovimientoDetalle[] = [];

  usuario: any;

  cargando = true;

  error = '';

  today = new Date();

  graficaCategorias: any;
  graficaMovimientos: any;
  graficaStock: any;

  graficaStockCritico: any;
graficaMovimientosMes: any;
graficaSemana: any;
semanaActual = 0;

fechaInicioSemana = '';
fechaFinSemana = '';

tituloSemana = '';

fechaInicioDashboard: string = '';
fechaFinDashboard: string = '';
tipoMovimientoDashboard: string = '';

movimientosOriginales: any[] = [];
movimientosFiltrados: any[] = [];

  ngOnInit(): void {

    if (!localStorage.getItem('usuario')) {

      window.location.href =
        '/login';

      return;
    }

    const datos =
      localStorage.getItem(
        'usuario'
      );

    if (datos) {

      this.usuario =
        JSON.parse(datos);
    }

    this.cargarDashboard();
  }

  cargarDashboard(): void {

    this.dashboardService
      .obtenerResumen()
      .subscribe({

        next: (data) => {

          this.resumen = data;
        },

        error: () => {

          this.error =
            'No se pudo cargar el resumen.';
        }

      });

    this.dashboardService
      .obtenerUltimosMovimientos()
      .subscribe({

       next: (data) => {

    this.movimientosOriginales = data;

this.movimientosFiltrados = [...data];

this.movimientos = this.movimientosFiltrados;

this.cargando = false;

    setTimeout(() => {

        this.cargarGraficas();
        this.cargarMovimientosSemana();

    }, 100);
},

        error: () => {

          this.error =
            'No se pudieron cargar los movimientos.';

          this.cargando = false;
        }

      });
  }

  cargarGraficas(): void {

    this.cargarProductosPorCategoria();

    this.cargarResumenMovimientos();

    this.cargarProductosMayorStock();
    this.cargarProductosMenorStock();
  }

  cargarProductosPorCategoria(): void {

    this.dashboardService
      .obtenerProductosPorCategoria()
      .subscribe(data => {

        const labels =
          data.map(x => x[0]);

        const valores =
          data.map(x => x[1]);

        this.graficaCategorias =
          new Chart(
            'graficaCategorias',
            {
              type: 'pie',
              data: {
                labels: labels,
                datasets: [{
    data: valores,
    backgroundColor: [
        '#2563eb',
        '#10b981',
        '#f59e0b',
        '#ef4444',
        '#8b5cf6'
    ],
    borderColor: '#ffffff',
    borderWidth: 2
}]
              },
              options: {
                responsive: true,
                plugins: {
                  title: {
                    display: true,
                    text:
                      'Distribución de productos por categoría'
                  }
                }
              }
            }
          );
      });
  }

  cargarResumenMovimientos(): void {

    this.dashboardService
      .obtenerResumenMovimientos()
      .subscribe(data => {

        const labels =
          data.map(x => x[0]);

        const valores =
          data.map(x => x[1]);

        this.graficaMovimientos =
          new Chart(
            'graficaMovimientos',
            {
              type: 'pie',
              data: {
                labels: labels,
                datasets: [{
    data: valores,
    backgroundColor: [
        '#2563eb',
        '#10b981',
        '#f59e0b',
        '#ef4444',
        '#8b5cf6'
    ],
    borderColor: '#ffffff',
    borderWidth: 2
}]
              },
             options: {
    responsive: true,
    maintainAspectRatio: true,
    devicePixelRatio: 2,
    plugins: {
        title: {
            display: true,
            text: 'Entradas vs Salidas'
        }
    }
}
            }
          );
      });
  }

  cargarProductosMayorStock(): void {

    this.dashboardService
      .obtenerProductosMayorStock()
      .subscribe(data => {

        const top5 =
          data.slice(0,5);

        const labels =
          top5.map(x => x[0]);

        const valores =
          top5.map(x => x[1]);

        this.graficaStock =
          new Chart(
            'graficaStock',
            {
              type: 'bar',
              data: {
                labels: labels,
                datasets:[{
    label:'Cantidad en stock',
    data: valores,
    backgroundColor:[
        '#2563eb',
        '#10b981',
        '#f59e0b',
        '#ef4444',
        '#8b5cf6'
    ]
}]
              },
              options:{
    responsive:true,
    maintainAspectRatio:false,

    layout:{
        padding:{
            top:30,
            bottom:20
        }
    },

    plugins:{
        title:{
            display:true,
            text:'Productos con mayor stock'
        }
    }
}
            }
          );
      });
  }

  cargarProductosMenorStock(): void {

  this.dashboardService
    .obtenerStockCritico()
    .subscribe(data => {

      const nombres = data.map(x => x[0]);
      const stockActual = data.map(x => x[1]);
      const stockMinimo = data.map(x => x[2]);

      if (this.graficaStockCritico) {
        this.graficaStockCritico.destroy();
      }

      this.graficaStockCritico = new Chart(
        'graficaStockCritico',
        {
          type: 'bar',
          data: {
            labels: nombres,
            datasets: [
              {
                label: 'Stock actual',
                data: stockActual,
                backgroundColor: '#ef4444'
              },
              {
                label: 'Stock mínimo',
                data: stockMinimo,
                backgroundColor: '#f59e0b'
              }
            ]
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
              y: {
                beginAtZero: true,
                ticks: {
                  precision: 0,
                  stepSize: 1
                }
              }
            },
            plugins: {
              title: {
                display: true,
                text: 'Productos en Stock Crítico'
              }
            }
          }
        }
      );
    });
}



semanaAnterior(): void {

  this.semanaActual--;

  this.cargarMovimientosSemana();
}

semanaSiguiente(): void {

  this.semanaActual++;

  this.cargarMovimientosSemana();
}

cargarMovimientosSemana() {

  const hoy = new Date();

const primerDia =
  new Date(hoy);

primerDia.setDate(
  hoy.getDate() -
  hoy.getDay() +
  1 +
  (this.semanaActual * 7)
);

const ultimoDia =
  new Date(primerDia);

ultimoDia.setDate(
  primerDia.getDate() + 6
);

this.fechaInicioSemana =
  primerDia.toLocaleDateString(
    'es-PE'
  );

this.fechaFinSemana =
  ultimoDia.toLocaleDateString(
    'es-PE'
  );

  this.dashboardService
    .obtenerMovimientosSemana(
      this.semanaActual
    )
    .subscribe(data => {

      const dias = [
        'Lunes',
        'Martes',
        'Miércoles',
        'Jueves',
        'Viernes',
        'Sábado',
        'Domingo'
      ];

      const entradas =
        [0,0,0,0,0,0,0];

      const salidas =
        [0,0,0,0,0,0,0];

      data.forEach(item => {

        const dia =
          item[0] - 2;

        const tipo =
          item[1];

        const total =
          item[2];

        if(
          dia >= 0 &&
          dia <= 6
        ){

          if(
            tipo === 'ENTRADA'
          ){
            entradas[dia] =
              total;
          }

          if(
            tipo === 'SALIDA'
          ){
            salidas[dia] =
              total;
          }
        }
      });

      if(
        this.graficaSemana
      ){
        this.graficaSemana.destroy();
      }

      this.graficaSemana =
        new Chart(
          'graficaSemana',
          {
            type:'bar',
            data:{
              labels:dias,
              datasets:[
{
    label:'Entradas',
    data:entradas,
    backgroundColor:'#22c55e',
    borderColor:'#16a34a',
    borderWidth:1,
    borderRadius:8
},
{
    label:'Salidas',
    data:salidas,
    backgroundColor:'#ef4444',
    borderColor:'#dc2626',
    borderWidth:1,
    borderRadius:8
}
]
            },
            options:{
              responsive:true,
              maintainAspectRatio:false,
              scales:{
                y:{
                  beginAtZero:true,
                  ticks:{
                    stepSize:1,
                    precision:0
                  }
                }
              },
              plugins:{
                title:{
                  display:true,
                  text:
                    'Movimientos de la semana'
                }
              }
            }
          }
        );

    });
}
irSemanaActual(): void {

    this.semanaActual = 0;

    this.cargarMovimientosSemana();
}

aplicarFiltrosDashboard(): void {

  this.movimientosFiltrados =
    this.movimientosOriginales.filter(m => {

      const fechaMovimiento =
        new Date(m.fecha);

      const cumpleInicio =
        !this.fechaInicioDashboard ||
        fechaMovimiento >=
        new Date(this.fechaInicioDashboard);

      const cumpleFin =
        !this.fechaFinDashboard ||
        fechaMovimiento <=
        new Date(
          this.fechaFinDashboard +
          'T23:59:59'
        );

      const cumpleTipo =
        !this.tipoMovimientoDashboard ||
        m.tipo ===
        this.tipoMovimientoDashboard;

      return (
        cumpleInicio &&
        cumpleFin &&
        cumpleTipo
      );
    });

  this.movimientos =
    this.movimientosFiltrados;
}
}
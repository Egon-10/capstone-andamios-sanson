import { Component,OnInit  } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CategoriaService }
from '../../services/categoria.service';
import { ReporteService }
from '../../services/reporte.service';

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './reportes.html',
  styleUrl: './reportes.css'
})
export class ReportesComponent implements OnInit{

  tipo: string = 'TODOS';
  fechaInicio = '';
fechaFin = '';
tipoReporte:string='MOVIMIENTOS';
categoria: string = '';
categorias:any[] = [];
accion = '';
rol = '';
  constructor(
  private reporteService: ReporteService,
  private categoriaService: CategoriaService
){}

ngOnInit(): void {

  this.categoriaService
      .listar()
      .subscribe(data => {
        this.categorias = data;
      });
}

  descargarReporte(): void {

  if(this.tipoReporte === 'MOVIMIENTOS'){

    this.reporteService
      .descargarMovimientos(
        this.tipo,
        this.fechaInicio,
        this.fechaFin
      )
      .subscribe(blob => {

        const url =
          window.URL.createObjectURL(blob);

        const a =
          document.createElement('a');

        a.href = url;
        a.download =
          'reporte_movimientos.pdf';

        a.click();

        window.URL.revokeObjectURL(url);
      });
  }

  if(this.tipoReporte === 'PRODUCTOS'){

    this.reporteService
      .descargarProductos(
        this.categoria
      )
      .subscribe(blob => {

        const url =
          window.URL.createObjectURL(blob);

        const a =
          document.createElement('a');

        a.href = url;
        a.download =
          'reporte_productos.pdf';

        a.click();

        window.URL.revokeObjectURL(url);
      });
  }

  if(this.tipoReporte === 'STOCK'){

    this.reporteService
      .descargarStockCritico(
        this.categoria
      )
      .subscribe(blob => {

        const url =
          window.URL.createObjectURL(blob);

        const a =
          document.createElement('a');

        a.href = url;
        a.download =
          'reporte_stock_critico.pdf';

        a.click();

        window.URL.revokeObjectURL(url);
      });
  }

  if(this.tipoReporte === 'AUDITORIA'){

    this.reporteService
      .descargarAuditoria(
        this.accion,
        this.fechaInicio,
        this.fechaFin
      )
      .subscribe(blob => {

        const url =
          window.URL.createObjectURL(blob);

        const a =
          document.createElement('a');

        a.href = url;
        a.download =
          'reporte_auditoria.pdf';

        a.click();

        window.URL.revokeObjectURL(url);
      });
  }

  if(this.tipoReporte === 'USUARIOS'){

    this.reporteService
      .descargarUsuarios(
        this.rol
      )
      .subscribe(blob => {

        const url =
          window.URL.createObjectURL(blob);

        const a =
          document.createElement('a');

        a.href = url;
        a.download =
          'reporte_usuarios.pdf';

        a.click();

        window.URL.revokeObjectURL(url);
      });
  }
}

}
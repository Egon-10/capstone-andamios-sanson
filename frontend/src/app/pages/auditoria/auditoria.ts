import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Auditoria } from '../../models/auditoria';
import { AuditoriaService } from '../../services/auditoria.service';

@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './auditoria.html',
  styleUrl: './auditoria.css'
})
export class AuditoriaComponent
implements OnInit {

  auditorias: Auditoria[] = [];

  auditoriasFiltradas: Auditoria[] = [];

  fechaInicio: string = '';

  fechaFin: string = '';

  textoBusqueda: string = '';
  tipoAccionFiltro: string = '';
  ordenFecha: string = 'ASC';

  constructor(
    private auditoriaService: AuditoriaService
  ) {}

  ngOnInit(): void {

    this.listarAuditorias();
  }

  listarAuditorias(): void {

    this.auditoriaService
      .listar()
      .subscribe(data => {

        this.auditorias = data;

        this.aplicarFiltros();

      });
  }

  aplicarFiltros(): void {

    const texto =
      this.textoBusqueda
      .toLowerCase()
      .trim();

    let resultado =
      this.auditorias.filter(a => {

        const fechaAuditoria =
          new Date(a.fecha!);

        const cumpleInicio =
          !this.fechaInicio ||

          fechaAuditoria >=
          new Date(this.fechaInicio);

        const cumpleFin =
          !this.fechaFin ||

          fechaAuditoria <=
          new Date(
            this.fechaFin +
            'T23:59:59'
          );

        const cumpleTexto =
          !texto ||

          a.accion
            ?.toLowerCase()
            .includes(texto);

            const cumpleTipoAccion =

  !this.tipoAccionFiltro ||

  a.accion
    ?.toUpperCase()
    .includes(
      this.tipoAccionFiltro
    );
        return cumpleInicio &&
       cumpleFin &&
       cumpleTexto &&
       cumpleTipoAccion;
               
      });
      if(this.ordenFecha === 'DESC'){

    resultado.sort(
      (a,b) =>
      new Date(b.fecha!).getTime() -
      new Date(a.fecha!).getTime()
    );

}else{

    resultado.sort(
      (a,b) =>
      new Date(a.fecha!).getTime() -
      new Date(b.fecha!).getTime()
    );

}

this.auditoriasFiltradas =
    resultado;
  }

}
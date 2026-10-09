import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from '../../services/auth.service';
import { CategoriaService } from '../../services/categoria.service';
import { ProductoService } from '../../services/producto.service';
import { RolService } from '../../services/rol.service';
import { UsuarioService } from '../../services/usuario.service';
import { FormatoReporte, ReporteService, TipoReporte } from '../../services/reporte.service';
import { mensajeDeErrorEnBlob } from '../../core/errores';
import { Categoria } from '../../models/categoria';
import { Producto } from '../../models/producto';
import { Rol } from '../../models/rol';
import { UsuarioOpcion } from '../../models/auditoria';

type Reporte = TipoReporte | 'kardex';

interface Opcion {
  tipo: Reporte;
  titulo: string;
  descripcion: string;
  icono: string;
  roles: string[];
}

const TODOS = ['ADMINISTRADOR', 'GERENTE', 'ENCARGADO'];
const GESTION = ['ADMINISTRADOR', 'GERENTE'];

/**
 * HU-26, HU-27 y HU-28: exportación de reportes en PDF y Excel.
 *
 * Solo se ofrecen los reportes que el rol puede pedir; el servidor aplica la
 * misma regla, de modo que ocultar la opción es comodidad y no el control.
 */
@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './reportes.html',
  styleUrl: './reportes.css'
})
export class ReportesComponent implements OnInit {

  private readonly auth = inject(AuthService);
  private readonly reportes = inject(ReporteService);
  private readonly categoriaService = inject(CategoriaService);
  private readonly productoService = inject(ProductoService);
  private readonly rolService = inject(RolService);
  private readonly usuarioService = inject(UsuarioService);

  readonly catalogo: Opcion[] = [
    { tipo: 'movimientos', titulo: 'Movimientos', icono: 'fa-right-left', roles: TODOS,
      descripcion: 'Entradas y salidas de un periodo, con sus totales.' },
    { tipo: 'stock-critico', titulo: 'Stock crítico', icono: 'fa-triangle-exclamation', roles: TODOS,
      descripcion: 'Productos agotados, críticos o bajos y cuánto pedir.' },
    { tipo: 'productos', titulo: 'Catálogo de productos', icono: 'fa-boxes-stacked', roles: TODOS,
      descripcion: 'Stock, umbrales y costo de cada producto activo.' },
    { tipo: 'kardex', titulo: 'Kardex de un producto', icono: 'fa-book', roles: TODOS,
      descripcion: 'Historial de un producto con su saldo acumulado.' },
    { tipo: 'valorizacion', titulo: 'Valorización', icono: 'fa-coins', roles: GESTION,
      descripcion: 'Valor del inventario al costo promedio, hoy o al cierre de una fecha.' },
    { tipo: 'auditoria', titulo: 'Bitácora de auditoría', icono: 'fa-clipboard-check', roles: GESTION,
      descripcion: 'Quién hizo qué y cuándo.' },
    { tipo: 'usuarios', titulo: 'Usuarios', icono: 'fa-users', roles: GESTION,
      descripcion: 'Cuentas con su rol y estado, sin datos personales.' },
    { tipo: 'accesos', titulo: 'Bitácora de accesos', icono: 'fa-right-to-bracket', roles: ['ADMINISTRADOR'],
      descripcion: 'Inicios de sesión exitosos, fallidos y bloqueados.' }
  ];

  opciones: Opcion[] = [];
  seleccionado: Reporte = 'movimientos';

  // Filtros
  tipoMovimiento = '';
  desde = '';
  hasta = '';
  categoriaId: number | null = null;
  productoId: number | null = null;
  fechaCorte = '';
  texto = '';
  usuarioId: number | null = null;
  resultado = '';
  rolId: number | null = null;
  estado = '';

  categorias: Categoria[] = [];
  productos: Producto[] = [];
  roles: Rol[] = [];
  usuarios: UsuarioOpcion[] = [];

  descargando: FormatoReporte | null = null;
  mensaje = '';
  error = '';
  readonly hoy = hoyEnLima();

  ngOnInit(): void {
    const rol = this.auth.rol;
    this.opciones = this.catalogo.filter(o => o.roles.includes(rol));
    this.categoriaService.listar().subscribe({ next: c => (this.categorias = c), error: () => {} });
    this.productoService.listar().subscribe({ next: p => (this.productos = p), error: () => {} });
    if (GESTION.includes(rol)) {
      this.rolService.listar().subscribe({ next: r => (this.roles = r), error: () => {} });
      this.usuarioService.opciones().subscribe({ next: u => (this.usuarios = u), error: () => {} });
    }
  }

  get opcionActual(): Opcion | undefined {
    return this.opciones.find(o => o.tipo === this.seleccionado);
  }

  elegir(tipo: Reporte): void {
    this.seleccionado = tipo;
    this.mensaje = '';
    this.error = '';
  }

  /** Valida lo que el servidor también valida, para avisar antes de pedir. */
  validar(): string {
    if (this.desde && this.hasta && this.desde > this.hasta) {
      return 'La fecha inicial no puede ser posterior a la final.';
    }
    if (this.seleccionado === 'kardex' && !this.productoId) {
      return 'Elija el producto del kardex.';
    }
    if (this.seleccionado === 'valorizacion' && this.fechaCorte && this.fechaCorte > this.hoy) {
      return 'La fecha de corte no puede ser posterior a hoy.';
    }
    return '';
  }

  filtros(): object {
    switch (this.seleccionado) {
      case 'movimientos':
        return { tipo: this.tipoMovimiento, desde: this.desde, hasta: this.hasta };
      case 'productos':
        return { categoriaId: this.categoriaId };
      case 'valorizacion':
        return { fecha: this.fechaCorte };
      case 'kardex':
        return { desde: this.desde, hasta: this.hasta };
      case 'auditoria':
        return { texto: this.texto, usuarioId: this.usuarioId, desde: this.desde, hasta: this.hasta };
      case 'accesos':
        return { texto: this.texto, resultado: this.resultado, desde: this.desde, hasta: this.hasta };
      case 'usuarios':
        return { texto: this.texto, rolId: this.rolId, estado: this.estado };
      default:
        return {};
    }
  }

  descargar(formato: FormatoReporte): void {
    this.mensaje = '';
    this.error = this.validar();
    if (this.error) {
      return;
    }
    this.descargando = formato;
    const peticion = this.seleccionado === 'kardex'
      ? this.reportes.descargarKardex(this.productoId!, formato, this.filtros())
      : this.reportes.descargar(this.seleccionado, formato, this.filtros());

    peticion.subscribe({
      next: nombre => {
        this.descargando = null;
        this.mensaje = `Se descargó ${nombre}.`;
      },
      error: (e: HttpErrorResponse) => {
        this.descargando = null;
        // El cuerpo del error llega como Blob y se lee de forma asincrona.
        void mensajeDeErrorEnBlob(e, 'No se pudo generar el reporte.').then(m => (this.error = m));
      }
    });
  }

  limpiar(): void {
    this.tipoMovimiento = '';
    this.desde = '';
    this.hasta = '';
    this.categoriaId = null;
    this.productoId = null;
    this.fechaCorte = '';
    this.texto = '';
    this.usuarioId = null;
    this.resultado = '';
    this.rolId = null;
    this.estado = '';
    this.error = '';
    this.mensaje = '';
  }

  usaPeriodo(): boolean {
    return ['movimientos', 'kardex', 'auditoria', 'accesos'].includes(this.seleccionado);
  }
}

/** Fecha de hoy en Lima en formato AAAA-MM-DD, para limitar los selectores. */
export function hoyEnLima(ahora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(ahora);
}

import { Routes } from '@angular/router';
import { authChildGuard, authGuard, loginGuard } from './core/auth.guard';
import { PerfilComponent } from './pages/perfil/perfil';
import { LoginComponent } from './pages/login/login';
import { LayoutComponent } from './components/layout/layout';
import { AuditoriaComponent } from './pages/auditoria/auditoria';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { ProductosComponent } from './pages/productos/productos';
import { CategoriasComponent } from './pages/categorias/categorias';
import { ProveedoresComponent } from './pages/proveedores/proveedores';
import { UsuariosComponent } from './pages/usuarios/usuarios';
import { MovimientosComponent } from './pages/movimientos/movimientos';
import {
  ReportesComponent
}
from './pages/reportes/reportes';
import { AjustesComponent } from './pages/ajustes/ajustes';
import { KardexComponent } from './pages/kardex/kardex';
import { ValorizacionComponent } from './pages/valorizacion/valorizacion';
import { CargaMasivaComponent } from './pages/carga-masiva/carga-masiva';

export const routes: Routes = [

  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },

  {
    path: 'login',
    component: LoginComponent,
    canActivate: [loginGuard]
  },

  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    canActivateChild: [authChildGuard],

    children: [

      {
        path: 'dashboard',
        component: DashboardComponent
      },

      {
        path: 'productos',
        component: ProductosComponent
      },

      {
        path: 'categorias',
        component: CategoriasComponent,
        data: { roles: ['ADMINISTRADOR'] }
      },

      {
        path: 'proveedores',
        component: ProveedoresComponent,
        data: { roles: ['ADMINISTRADOR', 'GERENTE'] }
      },

      {
        path: 'usuarios',
        component: UsuariosComponent,
        data: { roles: ['ADMINISTRADOR'] }
      },

      {
        path: 'movimientos',
        component: MovimientosComponent
      },

      {
  path: 'auditoria',
  component: AuditoriaComponent,
  data: { roles: ['ADMINISTRADOR', 'GERENTE'] }
},
{
    path: 'perfil',
    component: PerfilComponent
},
{
    path:'reportes',
    component: ReportesComponent
},

      // --- Sprint 2 ---

      // HU-16: el encargado registra el conteo y lo ve; los botones de aprobar
      // y rechazar solo aparecen para el administrador y el gerente, y el
      // servidor los restringe de todos modos.
      {
        path: 'ajustes',
        component: AjustesComponent
      },

      // HU-18: el kardex lo necesita tambien el encargado para operar.
      {
        path: 'kardex',
        component: KardexComponent
      },

      // HU-17: la valorizacion es informacion economica del negocio.
      {
        path: 'valorizacion',
        component: ValorizacionComponent,
        data: { roles: ['ADMINISTRADOR', 'GERENTE'] }
      },

      // HU-19: la carga masiva da de alta decenas de productos de una vez, asi
      // que queda al mismo nivel que el alta individual.
      {
        path: 'carga-masiva',
        component: CargaMasivaComponent,
        data: { roles: ['ADMINISTRADOR', 'GERENTE'] }
      }
    ]
  },

  {
    path: '**',
    redirectTo: 'login'
  }

];
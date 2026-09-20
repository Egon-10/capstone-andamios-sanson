import { Routes } from '@angular/router';
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

export const routes: Routes = [

  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },

  {
    path: 'login',
    component: LoginComponent
  },

  {
    path: '',
    component: LayoutComponent,

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
        component: CategoriasComponent
      },

      {
        path: 'proveedores',
        component: ProveedoresComponent
      },

      {
        path: 'usuarios',
        component: UsuariosComponent
      },

      {
        path: 'movimientos',
        component: MovimientosComponent
      },

      {
  path: 'auditoria',
  component: AuditoriaComponent
},
{
    path: 'perfil',
    component: PerfilComponent
},
{
    path:'reportes',
    component: ReportesComponent
}
    ]
  },

  {
    path: '**',
    redirectTo: 'login'
  }

];
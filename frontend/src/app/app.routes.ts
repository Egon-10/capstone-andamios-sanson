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
}
    ]
  },

  {
    path: '**',
    redirectTo: 'login'
  }

];
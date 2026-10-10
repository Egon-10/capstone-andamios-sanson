import { Routes } from '@angular/router';

import { authChildGuard, authGuard, cambioObligatorioGuard, loginGuard } from './core/auth.guard';
import { LoginComponent } from './pages/login/login';
import { LayoutComponent } from './components/layout/layout';

const GESTION = ['ADMINISTRADOR', 'GERENTE'];
const ADMINISTRADOR = ['ADMINISTRADOR'];

/**
 * Rutas de la aplicación.
 *
 * Cada pantalla se carga cuando se visita por primera vez (loadComponent) y no
 * al abrir la aplicación: el inicio de sesión descarga solo lo que necesita y
 * el panel aparece antes, sobre todo en la conexión móvil del almacén. Los
 * roles de cada ruta replican los del servidor (HU-05), que es quien decide.
 */
export const routes: Routes = [

  { path: '', redirectTo: 'login', pathMatch: 'full' },

  { path: 'login', component: LoginComponent, canActivate: [loginGuard] },

  // HU-36: con contraseña temporal, esta es la única pantalla disponible.
  {
    path: 'cambiar-password',
    canActivate: [cambioObligatorioGuard],
    loadComponent: () => import('./pages/cambiar-password/cambiar-password').then(m => m.CambiarPasswordComponent)
  },

  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    canActivateChild: [authChildGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.DashboardComponent) },
      { path: 'productos', loadComponent: () => import('./pages/productos/productos').then(m => m.ProductosComponent) },
      {
        path: 'categorias', data: { roles: ADMINISTRADOR },
        loadComponent: () => import('./pages/categorias/categorias').then(m => m.CategoriasComponent)
      },
      {
        path: 'proveedores', data: { roles: GESTION },
        loadComponent: () => import('./pages/proveedores/proveedores').then(m => m.ProveedoresComponent)
      },
      {
        path: 'usuarios', data: { roles: ADMINISTRADOR },
        loadComponent: () => import('./pages/usuarios/usuarios').then(m => m.UsuariosComponent)
      },
      { path: 'movimientos', loadComponent: () => import('./pages/movimientos/movimientos').then(m => m.MovimientosComponent) },
      {
        path: 'auditoria', data: { roles: GESTION },
        loadComponent: () => import('./pages/auditoria/auditoria').then(m => m.AuditoriaComponent)
      },
      { path: 'perfil', loadComponent: () => import('./pages/perfil/perfil').then(m => m.PerfilComponent) },
      { path: 'reportes', loadComponent: () => import('./pages/reportes/reportes').then(m => m.ReportesComponent) },

      // HU-16: el encargado registra el conteo; aprobar lo hacen administrador y gerente.
      { path: 'ajustes', loadComponent: () => import('./pages/ajustes/ajustes').then(m => m.AjustesComponent) },
      // HU-18: el kardex lo necesita también el encargado para operar.
      { path: 'kardex', loadComponent: () => import('./pages/kardex/kardex').then(m => m.KardexComponent) },
      // HU-17 y HU-28: la valorización es información económica del negocio.
      {
        path: 'valorizacion', data: { roles: GESTION },
        loadComponent: () => import('./pages/valorizacion/valorizacion').then(m => m.ValorizacionComponent)
      },
      // HU-19: la carga masiva da de alta decenas de productos de una vez.
      {
        path: 'carga-masiva', data: { roles: GESTION },
        loadComponent: () => import('./pages/carga-masiva/carga-masiva').then(m => m.CargaMasivaComponent)
      }
    ]
  },

  { path: '**', redirectTo: 'login' }
];

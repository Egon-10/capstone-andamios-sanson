import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

function verificar(ruta: ActivatedRouteSnapshot) {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.estaAutenticado()) {
    return router.createUrlTree(['/login']);
  }

  const rolesPermitidos = ruta.data['roles'] as string[] | undefined;
  if (rolesPermitidos && !rolesPermitidos.includes(auth.rol)) {
    return router.createUrlTree(['/dashboard']);
  }
  return true;
}

/**
 * HU-06: protege las rutas del cliente. La protección real está en el servidor
 * (HU-05); el guard evita mostrar pantallas que el usuario no puede usar.
 */
export const authGuard: CanActivateFn = ruta => verificar(ruta);

export const authChildGuard: CanActivateChildFn = ruta => verificar(ruta);

/** Si ya hay sesión, la pantalla de inicio de sesión redirige al panel. */
export const loginGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estaAutenticado() ? inject(Router).createUrlTree(['/dashboard']) : true;
};

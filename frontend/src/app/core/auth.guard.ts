import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

function verificar(ruta: ActivatedRouteSnapshot) {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.estaAutenticado()) {
    return router.createUrlTree(['/login']);
  }

  // HU-36: con contraseña temporal solo se puede ir a cambiarla. El servidor
  // aplica la misma regla; aquí se evita mostrar pantallas que fallarían.
  if (auth.debeCambiarPassword) {
    return router.createUrlTree(['/cambiar-password']);
  }

  const rolesPermitidos = ruta.data['roles'] as string[] | undefined;
  if (rolesPermitidos && !rolesPermitidos.includes(auth.rol)) {
    return router.createUrlTree(['/dashboard']);
  }
  return true;
}

/**
 * HU-06: protege las rutas del cliente.
 *
 * La protección real está en el servidor (HU-05); el guard solo evita mostrar
 * pantallas que el usuario no puede usar.
 *
 * Se declaran los dos parámetros que exige el tipo CanActivateFn de Angular,
 * aunque la decisión depende únicamente de la ruta: el estado del enrutador no
 * interviene.
 */
export const authGuard: CanActivateFn = (ruta, _estado) => verificar(ruta);

export const authChildGuard: CanActivateChildFn = (ruta, _estado) => verificar(ruta);

/** Si ya hay sesión, la pantalla de inicio de sesión redirige al panel. */
export const loginGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.estaAutenticado()) {
    return true;
  }
  return inject(Router).createUrlTree([auth.debeCambiarPassword ? '/cambiar-password' : '/dashboard']);
};

/** HU-36: la pantalla de cambio obligatorio solo tiene sentido con una contraseña temporal. */
export const cambioObligatorioGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.estaAutenticado()) {
    return router.createUrlTree(['/login']);
  }
  return auth.debeCambiarPassword ? true : router.createUrlTree(['/dashboard']);
};

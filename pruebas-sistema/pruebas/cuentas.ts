import { APIRequestContext, Page, expect } from '@playwright/test';

/** Cuenta de prueba creada por la CI para esta ejecución. */
export interface Cuenta {
  usuario: string;
  password: string;
}

function cuenta(prefijo: string): Cuenta {
  const usuario = process.env[`E2E_${prefijo}_USUARIO`];
  const password = process.env[`E2E_${prefijo}_PASSWORD`];
  if (!usuario || !password) {
    throw new Error(`Faltan E2E_${prefijo}_USUARIO y E2E_${prefijo}_PASSWORD`);
  }
  return { usuario, password };
}

export const cuentas = {
  get administrador() { return cuenta('ADMIN'); },
  get gerente() { return cuenta('GERENTE'); },
  get encargado() { return cuenta('ENCARGADO'); },
  /** Solo la usa la prueba de bloqueo: termina bloqueada. */
  get bloqueo() { return cuenta('BLOQUEO'); },
  /** La desactiva la prueba de desactivación inmediata. */
  get desactivar() { return cuenta('DESACTIVAR'); }
};

/** Inicia sesión desde la pantalla de acceso, como lo haría la persona. */
export async function entrar(page: Page, c: Cuenta): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Correo o nombre de usuario').fill(c.usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(c.password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page).toHaveURL(/\/(dashboard|cambiar-password)$/);
}

/** Token de la API para las pruebas que miden o consultan sin pantalla. */
export async function token(request: APIRequestContext, c: Cuenta): Promise<string> {
  const r = await request.post('/api/auth/login', { data: { correo: c.usuario, password: c.password } });
  expect(r.status(), await r.text()).toBe(200);
  return (await r.json()).accessToken;
}

/** Fecha de hoy en Lima (AAAA-MM-DD), con un desplazamiento en días. */
export function fechaLima(dias = 0): string {
  const d = new Date(Date.now() + dias * 86_400_000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(d);
}

/** Valor único corto para nombres y documentos de esta ejecución. */
export function unico(): string {
  return Date.now().toString(36).slice(-5) + Math.floor(Math.random() * 1000).toString().padStart(3, '0');
}

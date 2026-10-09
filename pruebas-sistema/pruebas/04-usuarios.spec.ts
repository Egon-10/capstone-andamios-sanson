import { test, expect, Page, Browser } from '@playwright/test';
import { cuentas, entrar, unico } from './cuentas';

/**
 * Administración de cuentas y bitácoras.
 * PA-08 a PA-12 · HU-30, HU-31, HU-32, HU-33, HU-36, HU-37, HU-38.
 */
async function irAUsuarios(page: Page): Promise<void> {
  await entrar(page, cuentas.administrador);
  await page.goto('/usuarios');
  await expect(page.getByRole('heading', { name: 'Gestión de usuarios' })).toBeVisible();
}

async function buscar(page: Page, texto: string): Promise<void> {
  await page.getByRole('searchbox').fill(texto);
  await expect(page.locator('tbody tr').filter({ hasText: texto }).first()).toBeVisible();
}

/** Guarda el formulario y, si el sistema lo rechaza, muestra por qué. */
async function guardarUsuario(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Guardar', exact: true }).click();
  const exito = page.getByRole('status').filter({ hasText: 'Usuario registrado correctamente' });
  const error = page.locator('[role="alert"], .ui-error-campo');
  await expect(exito.or(error).first()).toBeVisible();
  if (await error.count() > 0) {
    throw new Error('El alta fue rechazada: ' + (await error.allTextContents()).join(' | '));
  }
}

async function nuevaPagina(browser: Browser): Promise<Page> {
  const contexto = await browser.newContext();
  return contexto.newPage();
}

test.describe('Usuarios y seguridad de cuentas', () => {

  test('PA-08 alta de usuario: la política rechaza una clave débil y acepta una robusta (HU-30, HU-38)', async ({ page }) => {
    await irAUsuarios(page);
    const id = unico();
    const usuario = `e2e_${id}`;
    await page.locator('#u-nombres').fill('Prueba');
    await page.locator('#u-apellidos').fill('Sistema');
    await page.locator('#u-numeroDocumento').fill(String(70_000_000 + Math.floor(Math.random() * 9_999_999)));
    await page.locator('#u-correo').fill(`${usuario}@prueba.pe`);
    await page.locator('#u-nombreUsuario').fill(usuario);
    await page.locator('#u-rolId').selectOption({ label: 'ENCARGADO' });
    await page.locator('#u-area').selectOption('LOGISTICA');

    // Clave débil: el formulario no la envía y dice por qué.
    await page.locator('#u-password').fill('andamios123');
    await page.locator('#u-confirmarPassword').fill('andamios123');
    await page.getByRole('button', { name: 'Guardar', exact: true }).click();
    await expect(page.locator('#error-password')).toBeVisible();

    const clave = `Robusta#${id}Q7`;
    await page.locator('#u-password').fill(clave);
    await page.locator('#u-confirmarPassword').fill(clave);
    await guardarUsuario(page);
    await buscar(page, usuario);

    // La cuenta nueva puede entrar con la clave elegida.
    const otra = await nuevaPagina(page.context().browser()!);
    await entrar(otra, { usuario, password: clave });
    await expect(otra).toHaveURL(/\/dashboard$/);
    await otra.context().close();
  });

  test('PA-09 contraseña temporal: el administrador la genera y la persona debe cambiarla al entrar (HU-36, HU-37)', async ({ page, browser }) => {
    // Cuenta propia de la prueba, creada por el administrador.
    await irAUsuarios(page);
    const id = unico();
    const usuario = `e2e_${id}`;
    const clave = `Inicial#${id}W3`;
    await page.locator('#u-nombres').fill('Temporal');
    await page.locator('#u-apellidos').fill('Sistema');
    await page.locator('#u-numeroDocumento').fill(String(60_000_000 + Math.floor(Math.random() * 9_999_999)));
    await page.locator('#u-correo').fill(`${usuario}@prueba.pe`);
    await page.locator('#u-nombreUsuario').fill(usuario);
    await page.locator('#u-password').fill(clave);
    await page.locator('#u-confirmarPassword').fill(clave);
    await page.locator('#u-rolId').selectOption({ label: 'ENCARGADO' });
    await page.locator('#u-area').selectOption('LOGISTICA');
    await guardarUsuario(page);

    await buscar(page, usuario);
    await page.getByRole('button', { name: `Restablecer la contraseña de ${usuario}` }).click();
    await page.getByRole('button', { name: 'Generar contraseña temporal' }).click();
    const temporal = (await page.locator('.temporal code').textContent())!.trim();
    expect(temporal.length).toBeGreaterThanOrEqual(12);
    await page.getByRole('button', { name: 'Listo' }).click();

    // La clave anterior ya no sirve.
    const persona = await nuevaPagina(browser);
    await persona.goto('/login');
    await persona.getByLabel('Correo o nombre de usuario').fill(usuario);
    await persona.getByLabel('Contraseña', { exact: true }).fill(clave);
    await persona.getByRole('button', { name: 'Ingresar' }).click();
    await expect(persona.getByRole('alert')).toHaveText(/incorrectos/);

    // Con la temporal entra solo a la pantalla de cambio.
    await entrar(persona, { usuario, password: temporal });
    await expect(persona).toHaveURL(/\/cambiar-password$/);
    await persona.goto('/productos');
    await expect(persona).toHaveURL(/\/cambiar-password$/);

    const nueva = `Personal#${id}K5`;
    await persona.getByLabel('Contraseña temporal', { exact: true }).fill(temporal);
    await persona.getByLabel('Nueva contraseña', { exact: true }).fill(nueva);
    await persona.getByLabel('Confirme la nueva contraseña').fill(nueva);
    await persona.getByRole('button', { name: 'Guardar y continuar' }).click();
    await expect(persona).toHaveURL(/\/dashboard$/);
    await persona.context().close();
  });

  test('PA-10 desactivar una cuenta cierra su sesión abierta de inmediato (HU-31)', async ({ page, browser }) => {
    const c = cuentas.desactivar;
    const persona = await nuevaPagina(browser);
    await entrar(persona, c);

    await irAUsuarios(page);
    await buscar(page, c.usuario);
    await page.getByRole('button', { name: `Desactivar a ${c.usuario}` }).click();
    await page.getByLabel(/Motivo/).fill('Prueba de sistema PA-10: cese laboral');
    await page.getByRole('button', { name: 'Desactivar cuenta' }).click();
    await expect(page.getByText(`Se desactivó la cuenta ${c.usuario}`)).toBeVisible();

    // La sesión que la persona tenía abierta deja de servir en la siguiente acción.
    await persona.goto('/productos');
    await expect(persona).toHaveURL(/\/login/, { timeout: 15_000 });
    await persona.context().close();
  });

  test('PA-11 la auditoría registra quién hizo cada acción y los accesos fallidos (HU-32, HU-33)', async ({ page }) => {
    await entrar(page, cuentas.administrador);
    await page.goto('/auditoria');
    await expect(page.locator('tbody tr').filter({ hasText: `Se desactivó la cuenta ${cuentas.desactivar.usuario}` }).first()).toBeVisible();

    await page.getByRole('tab', { name: /Accesos/ }).click();
    await expect(page.locator('tbody tr').filter({ hasText: cuentas.bloqueo.usuario }).first()).toBeVisible();
  });

  test('PS-10 la contraseña no se puede cambiar por la edición del perfil (HU-37)', async ({ page }) => {
    await entrar(page, cuentas.gerente);
    const token = await page.evaluate(() => localStorage.getItem('accessToken'));
    const r = await page.request.patch('/api/usuarios/me', {
      headers: { Authorization: `Bearer ${token}` },
      data: { password: 'OtraClave#2026x' }
    });
    expect(r.status()).toBeGreaterThanOrEqual(400);
    expect(r.status()).toBeLessThan(500);
  });
});

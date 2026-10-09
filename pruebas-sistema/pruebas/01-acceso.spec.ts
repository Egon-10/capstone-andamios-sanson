import { test, expect } from '@playwright/test';
import { cuentas, entrar } from './cuentas';

/**
 * Acceso y control por rol.
 * PS-01 a PS-05 · HU-03, HU-05, HU-08, HU-35.
 */
test.describe('Acceso al sistema', () => {

  test('PS-01 credenciales inválidas: mensaje genérico sin revelar qué falló (HU-03)', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Correo o nombre de usuario').fill('no.existe.e2e');
    await page.getByLabel('Contraseña', { exact: true }).fill('Incorrecta#2026');
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(page.getByRole('alert')).toHaveText(/Usuario o contraseña incorrectos/);
    await expect(page).toHaveURL(/\/login/);
  });

  test('PA-01 el administrador entra al panel y ve todas las opciones (HU-03, HU-05)', async ({ page }) => {
    await entrar(page, cuentas.administrador);
    await expect(page.getByRole('heading', { name: /Bienvenido/ })).toBeVisible();
    const menu = page.getByRole('navigation', { name: 'Menú principal' });
    for (const opcion of ['Productos', 'Usuarios', 'Movimientos', 'Valorización', 'Auditoría', 'Reportes']) {
      await expect(menu.getByRole('link', { name: opcion })).toBeVisible();
    }
  });

  test('PA-02 el encargado no ve ni puede abrir las opciones restringidas (HU-05)', async ({ page }) => {
    await entrar(page, cuentas.encargado);
    const menu = page.getByRole('navigation', { name: 'Menú principal' });
    await expect(menu.getByRole('link', { name: 'Movimientos' })).toBeVisible();
    await expect(menu.getByRole('link', { name: 'Usuarios' })).toHaveCount(0);
    await expect(menu.getByRole('link', { name: 'Valorización' })).toHaveCount(0);

    // Escribir la dirección a mano tampoco abre la pantalla.
    await page.goto('/usuarios');
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('PS-02 el servidor rechaza la operación aunque se llame directo a la API (HU-05)', async ({ page }) => {
    await entrar(page, cuentas.encargado);
    const token = await page.evaluate(() => localStorage.getItem('accessToken'));
    const r = await page.request.get('/api/usuarios', { headers: { Authorization: `Bearer ${token}` } });
    expect(r.status()).toBe(403);
  });

  test('PS-03 cerrar sesión invalida el acceso a las pantallas (HU-08)', async ({ page }) => {
    await entrar(page, cuentas.gerente);
    await page.getByRole('button', { name: /GERENTE/ }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  test('PA-03 cinco intentos fallidos bloquean la cuenta 15 minutos (HU-35)', async ({ page }) => {
    const c = cuentas.bloqueo;
    await page.goto('/login');
    await page.getByLabel('Correo o nombre de usuario').fill(c.usuario);
    for (let i = 1; i <= 5; i++) {
      await page.getByLabel('Contraseña', { exact: true }).fill(`Equivocada#${i}x2026`);
      await page.getByRole('button', { name: 'Ingresar' }).click();
      await expect(page.getByRole('alert')).toBeVisible();
    }
    await expect(page.getByRole('alert')).toHaveText(/bloqueada temporalmente/);

    // Con la cuenta bloqueada, ni la contraseña correcta permite entrar.
    await page.getByLabel('Contraseña', { exact: true }).fill(c.password);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(page.getByRole('alert')).toHaveText(/bloqueada temporalmente/);
    await expect(page).toHaveURL(/\/login/);
  });
});

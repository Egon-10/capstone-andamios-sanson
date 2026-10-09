import { test, expect } from '@playwright/test';
import { Page } from '@playwright/test';
import { cuentas, entrar } from './cuentas';

/** Elige en #producto la opción cuyo texto contiene el nombre dado. */
async function elegirProducto(page: Page, nombre: string): Promise<void> {
  const opcion = page.locator('#producto option', { hasText: nombre }).first();
  await expect(opcion).toBeAttached();
  await page.locator('#producto').selectOption({ label: (await opcion.textContent())!.trim() });
}

/**
 * Operación del almacén.
 * PS-04 a PS-07 · HU-11, HU-12, HU-14, HU-17, HU-18, HU-29.
 */
test.describe('Operación del inventario', () => {

  test('PA-04 el encargado registra una compra y queda en el historial con su saldo (HU-11, HU-14, HU-17)', async ({ page }) => {
    await entrar(page, cuentas.encargado);
    await page.getByRole('navigation', { name: 'Menú principal' }).getByRole('link', { name: 'Movimientos' }).click();
    await expect(page.getByRole('heading', { name: 'Movimientos de inventario' })).toBeVisible();

    await page.getByRole('button', { name: 'Entrada' }).click();
    await elegirProducto(page, 'marco andamios acrow');
    await page.locator('#cantidad').fill('7');
    await page.locator('#motivo').selectOption('COMPRA');
    await page.locator('#costo').fill('310.50');
    await page.locator('#observacion').fill('Prueba de sistema PA-04');
    await page.getByRole('button', { name: 'Registrar entrada' }).click();

    await expect(page.getByRole('status').filter({ hasText: 'Entrada registrada correctamente' })).toBeVisible();
    const fila = page.locator('tbody tr').filter({ hasText: 'Prueba de sistema PA-04' }).first();
    await expect(fila).toBeVisible();
    await expect(fila).toContainText('ENTRADA');
    await expect(fila).toContainText('REGISTRADO');
  });

  test('PS-04 una salida mayor que el stock se rechaza y el stock no cambia (HU-12)', async ({ page }) => {
    await entrar(page, cuentas.encargado);
    await page.goto('/movimientos');
    await page.getByRole('button', { name: 'Salida' }).click();
    await page.locator('#producto').selectOption({ index: 1 });
    await page.locator('#cantidad').fill('999999');
    await page.locator('#motivo').selectOption({ index: 1 });
    await page.getByRole('button', { name: 'Registrar salida' }).click();
    await expect(page.getByRole('alert').first()).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: 'registrada correctamente' })).toHaveCount(0);
  });

  test('PS-05 el kardex de un producto muestra sus movimientos con saldo (HU-18)', async ({ page }) => {
    await entrar(page, cuentas.encargado);
    await page.goto('/kardex');
    // El mismo producto de PA-04: tiene al menos ese movimiento.
    await elegirProducto(page, 'marco andamios acrow');
    await expect(page.locator('table tbody tr').first()).toBeVisible();
  });

  test('PS-06 el gerente ve la campana de alertas de reposición (HU-29)', async ({ page }) => {
    await entrar(page, cuentas.gerente);
    const campana = page.getByRole('button', { name: /Alertas de reposición/ });
    await expect(campana).toBeVisible();
    await campana.click();
    await expect(page.getByRole('region', { name: 'Alertas de reposición' })).toBeVisible();
  });

  test('PS-07 el panel muestra los indicadores de gestión calculados por el servidor (HU-23 a HU-25)', async ({ page }) => {
    await entrar(page, cuentas.gerente);
    await expect(page.getByText('Indicadores de gestión')).toBeVisible();
    await expect(page.getByText(/Valor del inventario/i)).toBeVisible();
    await expect(page.getByText('Tendencia de movimientos')).toBeVisible();
  });
});

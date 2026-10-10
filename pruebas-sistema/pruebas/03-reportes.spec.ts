import { test, expect, Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { cuentas, entrar, fechaLima } from './cuentas';

/**
 * Reportes y valorización.
 * PS-08 a PS-10 y PA-05 a PA-07 · HU-26, HU-27, HU-28.
 */
async function descargar(page: Page, boton: string): Promise<{ nombre: string; bytes: Buffer }> {
  const [descarga] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: boton }).click()
  ]);
  const ruta = await descarga.path();
  return { nombre: descarga.suggestedFilename(), bytes: await readFile(ruta!) };
}

/** Las opciones son tarjetas: se elige con un clic sobre la tarjeta, como la persona. */
async function elegirReporte(page: Page, titulo: string): Promise<void> {
  await page.locator('label.opcion', { has: page.getByText(titulo, { exact: true }) }).click();
  await expect(page.getByRole('radio', { name: new RegExp('^' + titulo) })).toBeChecked();
}

test.describe('Reportes', () => {

  test('PA-05 el gerente descarga el reporte de movimientos en PDF (HU-26)', async ({ page }) => {
    await entrar(page, cuentas.gerente);
    await page.goto('/reportes');
    await elegirReporte(page, 'Movimientos');
    const { nombre, bytes } = await descargar(page, 'Descargar PDF');
    expect(nombre).toMatch(/\.pdf$/);
    expect(bytes.subarray(0, 5).toString('latin1')).toBe('%PDF-');
    expect(bytes.length).toBeGreaterThan(1000);
  });

  test('PA-06 el gerente descarga el stock crítico en Excel (HU-27)', async ({ page }) => {
    await entrar(page, cuentas.gerente);
    await page.goto('/reportes');
    await elegirReporte(page, 'Stock crítico');
    const { nombre, bytes } = await descargar(page, 'Descargar Excel');
    expect(nombre).toMatch(/\.xlsx$/);
    // Un .xlsx es un ZIP: empieza con la firma "PK".
    expect(bytes.subarray(0, 2).toString('latin1')).toBe('PK');
  });

  test('PS-08 el encargado solo ve los reportes operativos (HU-26)', async ({ page }) => {
    await entrar(page, cuentas.encargado);
    await page.goto('/reportes');
    await expect(page.getByRole('radio', { name: /^Movimientos/ })).toBeVisible();
    await expect(page.getByRole('radio', { name: /^Valorización/ })).toHaveCount(0);
    await expect(page.getByRole('radio', { name: /^Bitácora de accesos/ })).toHaveCount(0);
  });

  test('PA-07 la valorización se reconstruye al cierre de un día anterior y se exporta (HU-28)', async ({ page }) => {
    await entrar(page, cuentas.gerente);
    await page.goto('/valorizacion');
    await expect(page.getByText('Valor total')).toBeVisible();

    await page.getByLabel('Valorizar al cierre del día').fill(fechaLima(-1));
    await page.getByRole('button', { name: 'Aplicar corte' }).click();
    await expect(page.getByText(/Valorización reconstruida al cierre del/)).toBeVisible();

    const { nombre, bytes } = await descargar(page, 'PDF');
    expect(nombre).toMatch(/\.pdf$/);
    expect(bytes.subarray(0, 5).toString('latin1')).toBe('%PDF-');
  });

  test('PS-09 la valorización con fecha futura se rechaza (HU-28)', async ({ page, request }) => {
    await entrar(page, cuentas.gerente);
    const token = await page.evaluate(() => localStorage.getItem('accessToken'));
    const r = await request.get(`/api/inventario/valorizacion?fecha=${fechaLima(3)}`,
      { headers: { Authorization: `Bearer ${token}` } });
    expect([400, 422]).toContain(r.status());
  });
});

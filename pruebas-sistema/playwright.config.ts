import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas de sistema (PS) y de aceptación (PA) del Sprint 4.
 *
 * Se ejecutan en la CI contra el despliegue preliminar levantado con
 * docker compose: el navegador entra por Nginx, como lo haría un usuario,
 * y cada operación pasa por la API real y la base MySQL migrada.
 *
 * Las cuentas de prueba las crea la CI con contraseñas aleatorias en cada
 * ejecución y llegan por variables de entorno (ver cuentas.ts).
 */
export default defineConfig({
  testDir: './pruebas',
  // Un solo trabajador: varias pruebas cambian el estado de la base (stock,
  // cuentas) y el orden importa más que la velocidad.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [
    ['list'],
    ['github'],
    ['json', { outputFile: 'resultados/resultados.json' }],
    ['html', { outputFolder: 'resultados/informe', open: 'never' }]
  ],
  outputDir: 'resultados/evidencias',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    acceptDownloads: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome']
  }
});

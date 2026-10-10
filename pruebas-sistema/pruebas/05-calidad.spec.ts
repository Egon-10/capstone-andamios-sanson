import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { cuentas, entrar, token } from './cuentas';

/**
 * Atributos de calidad: accesibilidad, seguridad del despliegue y desempeño.
 * PS-11 a PS-14 · HU-38, HU-39, HU-41, HU-42 y requisitos no funcionales.
 *
 * Las mediciones se guardan en resultados/mediciones.json; la CI las publica
 * en el resumen de la ejecución y alimentan los indicadores del informe.
 */
const mediciones: Record<string, unknown> = {};

test.afterAll(async () => {
  await mkdir('resultados', { recursive: true });
  await writeFile('resultados/mediciones.json', JSON.stringify(mediciones, null, 2));
});

function percentil(valores: number[], p: number): number {
  const orden = [...valores].sort((a, b) => a - b);
  return orden[Math.min(orden.length - 1, Math.ceil((p / 100) * orden.length) - 1)];
}

test.describe('Calidad del producto', () => {

  test('PS-11 accesibilidad WCAG 2.1 AA: sin infracciones críticas en las pantallas principales (HU-39)', async ({ page }) => {
    const resultado: Record<string, { criticas: number; serias: number; reglas: string[] }> = {};

    async function revisar(nombre: string): Promise<void> {
      const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      const criticas = r.violations.filter(v => v.impact === 'critical');
      const serias = r.violations.filter(v => v.impact === 'serious');
      resultado[nombre] = {
        criticas: criticas.length,
        serias: serias.length,
        reglas: [...criticas, ...serias].map(v => `${v.impact}: ${v.id} (${v.nodes.length})`)
      };
    }

    await page.goto('/login');
    await revisar('login');
    await entrar(page, cuentas.administrador);
    for (const ruta of ['dashboard', 'productos', 'movimientos', 'kardex', 'reportes', 'usuarios', 'auditoria', 'valorizacion']) {
      await page.goto(`/${ruta}`);
      await page.waitForLoadState('networkidle');
      await revisar(ruta);
    }
    mediciones['accesibilidad'] = resultado;

    const conCriticas = Object.entries(resultado).filter(([, r]) => r.criticas > 0);
    expect(conCriticas, JSON.stringify(conCriticas, null, 2)).toEqual([]);
  });

  test('PS-12 cabeceras de seguridad del sitio y estado interno no publicado (HU-38, HU-42)', async ({ request }) => {
    const r = await request.get('/');
    expect(r.status()).toBe(200);
    const h = r.headers();
    expect(h['content-security-policy']).toContain("default-src 'self'");
    expect(h['content-security-policy']).toContain("frame-ancestors 'none'");
    expect(h['x-frame-options']).toBe('DENY');
    expect(h['x-content-type-options']).toBe('nosniff');
    expect(h['referrer-policy']).toBe('no-referrer');
    expect(h['server'] ?? '').not.toMatch(/\d/);

    // DEF-20: con la CSP del sitio no se ejecutan manejadores en línea; una
    // hoja de estilos cargada con onload nunca se aplicaría.
    expect(await r.text()).not.toMatch(/\son\w+=/);

    // El estado del backend solo se consulta desde la red interna.
    expect((await request.get('/actuator/health')).status()).toBe(404);

    // Cada respuesta de la API trae el identificador de la solicitud (HU-41).
    const api = await request.post('/api/auth/login', { data: { correo: 'nadie', password: 'x' } });
    expect(api.headers()['x-request-id']).toBeTruthy();
    mediciones['cabeceras'] = { csp: h['content-security-policy'], xFrameOptions: h['x-frame-options'] };
  });

  test('PS-13 desempeño de la API: p95 menor a 1 segundo en las consultas frecuentes (RNF de rendimiento)', async ({ request }) => {
    const t = await token(request, cuentas.gerente);
    const consultas = [
      '/api/dashboard/resumen',
      '/api/indicadores/resumen',
      '/api/productos/buscar?pagina=0&tamano=20',
      '/api/movimientos',
      '/api/inventario/valorizacion'
    ];
    const repeticiones = 30;
    const resultado: Record<string, { p50: number; p95: number; max: number; n: number }> = {};

    for (const ruta of consultas) {
      const tiempos: number[] = [];
      for (let i = 0; i < repeticiones; i++) {
        const inicio = performance.now();
        const r = await request.get(ruta, { headers: { Authorization: `Bearer ${t}` } });
        tiempos.push(performance.now() - inicio);
        expect(r.status(), ruta).toBe(200);
      }
      resultado[ruta] = {
        p50: Math.round(percentil(tiempos, 50)),
        p95: Math.round(percentil(tiempos, 95)),
        max: Math.round(Math.max(...tiempos)),
        n: repeticiones
      };
    }
    mediciones['apiMs'] = resultado;

    // Indicador "tiempo de obtención del reporte de stock": lo que tarda el
    // sistema en generar el archivo (la medición de campo incluye además el
    // tiempo de la persona y se registra aparte).
    const reportes: Record<string, number[]> = { pdf: [], xlsx: [] };
    for (const formato of ['pdf', 'xlsx'] as const) {
      for (let i = 0; i < 10; i++) {
        const inicio = performance.now();
        const r = await request.get(`/api/reportes/stock-critico?formato=${formato}`, { headers: { Authorization: `Bearer ${t}` } });
        reportes[formato].push(performance.now() - inicio);
        expect(r.status()).toBe(200);
      }
    }
    mediciones['reporteStockMs'] = {
      pdf: { p50: Math.round(percentil(reportes.pdf, 50)), p95: Math.round(percentil(reportes.pdf, 95)) },
      xlsx: { p50: Math.round(percentil(reportes.xlsx, 50)), p95: Math.round(percentil(reportes.xlsx, 95)) }
    };

    for (const [ruta, r] of Object.entries(resultado)) {
      expect(r.p95, `${ruta}: p95 ${r.p95} ms`).toBeLessThan(1000);
    }
  });

  test('PS-14 desempeño percibido: el panel queda listo en menos de 3 segundos (RNF de rendimiento)', async ({ page }) => {
    await entrar(page, cuentas.gerente);
    const tiempos: number[] = [];
    for (let i = 0; i < 5; i++) {
      const inicio = Date.now();
      await page.goto('/dashboard');
      await expect(page.getByText('Indicadores de gestión')).toBeVisible();
      await page.waitForLoadState('networkidle');
      tiempos.push(Date.now() - inicio);
    }
    const navegacion = await page.evaluate(() => {
      const n = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;
      return { domContentLoaded: Math.round(n.domContentLoadedEventEnd), carga: Math.round(n.loadEventEnd), bytes: n.transferSize };
    });
    mediciones['panel'] = { listoMs: tiempos, mediana: percentil(tiempos, 50), navegacion };
    expect(percentil(tiempos, 50)).toBeLessThan(3000);
  });
});

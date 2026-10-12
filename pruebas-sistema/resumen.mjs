// Publica el resultado de las pruebas de sistema como anotaciones de la CI.
// GitHub admite pocas anotaciones por paso, así que se agrupan: una con el
// conteo, una con la lista de pruebas y una con las mediciones.
import { readFileSync, existsSync, appendFileSync } from 'node:fs';

const escapar = t => t.replace(/%/g, '%25').replace(/\r?\n/g, '%0A');
const leer = r => (existsSync(r) ? JSON.parse(readFileSync(r, 'utf8')) : null);

const resultados = leer('resultados/resultados.json');
if (!resultados) {
  console.log('::error title=Pruebas de sistema::No se generó resultados/resultados.json');
  process.exit(0);
}

const pruebas = [];
const recorrer = suite => {
  for (const s of suite.suites ?? []) recorrer(s);
  for (const spec of suite.specs ?? []) {
    const r = spec.tests?.[0]?.results?.at(-1);
    pruebas.push({ titulo: spec.title, estado: r?.status ?? 'skipped', ms: r?.duration ?? 0 });
  }
};
for (const s of resultados.suites) recorrer(s);

const ok = pruebas.filter(p => p.estado === 'passed').length;
const fallas = pruebas.filter(p => p.estado !== 'passed' && p.estado !== 'skipped');
const lista = pruebas.map(p => `${p.estado === 'passed' ? 'OK   ' : 'FALLA'} ${p.titulo} (${Math.round(p.ms)} ms)`).join('\n');

console.log(`::notice title=Pruebas de sistema y aceptación::${ok} de ${pruebas.length} pasaron, ${fallas.length} fallaron`);
console.log(`::notice title=Detalle de pruebas de sistema::${escapar(lista)}`);

const mediciones = leer('resultados/mediciones.json');
if (mediciones) {
  console.log(`::notice title=Mediciones de calidad::${escapar(JSON.stringify(mediciones, null, 1))}`);
}

if (process.env.GITHUB_STEP_SUMMARY) {
  appendFileSync(process.env.GITHUB_STEP_SUMMARY,
    `## Pruebas de sistema y aceptación\n\n${ok} de ${pruebas.length} pasaron.\n\n\`\`\`\n${lista}\n\`\`\`\n\n` +
    (mediciones ? `### Mediciones\n\n\`\`\`json\n${JSON.stringify(mediciones, null, 2)}\n\`\`\`\n` : ''));
}

/**
 * HU-37: política de complejidad de contraseñas, la misma que aplica el
 * servidor (PoliticaContrasena.java). En el cliente sirve para mostrar, mientras
 * el usuario escribe, qué reglas cumple y cuáles le faltan; la decisión final la
 * toma siempre el servidor.
 */
export interface ReglaContrasena {
  id: string;
  texto: string;
  cumple: (clave: string, usuario?: string | null, documento?: string | null) => boolean;
}

export const LARGO_MINIMO = 10;
export const LARGO_MAXIMO = 72;

const PREVISIBLES = new Set([
  'andamios2026', 'andamios2025', 'sanson2026', 'inventario2026', 'password123',
  'contrasena123', 'administrador1', 'qwerty12345', '1234567890'
]);

/** Minúsculas y solo letras y números: "Andamios-2026!" y "andamios2026" son la misma idea. */
export function normalizar(valor: string): string {
  return valor.toLowerCase().normalize('NFD').replace(/[^\p{L}\p{Nd}]/gu, '');
}

function contiene(clave: string, dato?: string | null): boolean {
  if (!dato) {
    return false;
  }
  const d = normalizar(dato);
  return d.length >= 4 && normalizar(clave).includes(d);
}

export const REGLAS: ReglaContrasena[] = [
  { id: 'largo', texto: `Entre ${LARGO_MINIMO} y ${LARGO_MAXIMO} caracteres`,
    cumple: c => c.length >= LARGO_MINIMO && new TextEncoder().encode(c).length <= LARGO_MAXIMO },
  { id: 'mayuscula', texto: 'Una letra mayúscula', cumple: c => /\p{Lu}/u.test(c) },
  { id: 'minuscula', texto: 'Una letra minúscula', cumple: c => /\p{Ll}/u.test(c) },
  { id: 'numero', texto: 'Un número', cumple: c => /\d/.test(c) },
  { id: 'simbolo', texto: 'Un símbolo, por ejemplo ! # $ % & * @', cumple: c => /[^\p{L}\p{Nd}]/u.test(c) },
  { id: 'espacios', texto: 'Sin espacios', cumple: c => c.length > 0 && !/\s/.test(c) },
  { id: 'datos', texto: 'Sin su nombre de usuario ni su documento',
    cumple: (c, u, d) => c.length > 0 && !contiene(c, u) && !contiene(c, d) },
  { id: 'previsible', texto: 'Que no sea una contraseña previsible',
    cumple: c => c.length > 0 && !PREVISIBLES.has(normalizar(c)) }
];

/** Reglas que la contraseña incumple. Vacío si es válida. */
export function faltas(clave: string, usuario?: string | null, documento?: string | null): ReglaContrasena[] {
  return REGLAS.filter(r => !r.cumple(clave ?? '', usuario, documento));
}

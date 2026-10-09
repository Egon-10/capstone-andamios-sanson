# Pruebas de sistema y de aceptación

Pruebas de extremo a extremo (Playwright) contra el sistema desplegado con
`docker compose`: el navegador entra por Nginx y cada operación pasa por la
API y la base MySQL reales.

| Archivo | Pruebas | Historias |
|---|---|---|
| `01-acceso.spec.ts` | PS-01 a PS-03, PA-01 a PA-03 | HU-03, HU-05, HU-08, HU-35 |
| `02-inventario.spec.ts` | PA-04, PS-04 a PS-07 | HU-11, HU-12, HU-14, HU-17, HU-18, HU-23 a HU-25, HU-29 |
| `03-reportes.spec.ts` | PA-05 a PA-07, PS-08, PS-09 | HU-26, HU-27, HU-28 |
| `04-usuarios.spec.ts` | PA-08 a PA-11, PS-10 | HU-30 a HU-33, HU-36 a HU-38 |
| `05-calidad.spec.ts` | PS-11 a PS-14 | HU-38, HU-39, HU-41, HU-42, rendimiento |

PS = prueba de sistema; PA = prueba de aceptación (criterio de una historia
de usuario verificado tal como lo usaría la persona).

## Cómo se ejecutan

La CI levanta el despliegue, crea cuentas de prueba con contraseñas
aleatorias (`crear-cuentas.py`) y ejecuta:

```bash
npm ci
npx playwright install --with-deps chromium
BASE_URL=http://localhost:8080 npx playwright test
node resumen.mjs   # publica el resultado y las mediciones
```

Para ejecutarlas en local hacen falta las variables `E2E_<ROL>_USUARIO` y
`E2E_<ROL>_PASSWORD` de las cuentas ADMIN, GERENTE, ENCARGADO, BLOQUEO y
DESACTIVAR. Las pruebas cambian datos (registran movimientos, crean,
bloquean y desactivan cuentas): no se deben ejecutar contra producción.

Las mediciones de accesibilidad, cabeceras y tiempos de respuesta quedan en
`resultados/mediciones.json`.

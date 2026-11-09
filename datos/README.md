# Datos de la empresa — catálogo y lista de precios

| Archivo | Contenido |
|---|---|
| `precios-sanson-2026-11.pdf` | Lista de precios original entregada por la empresa (3 páginas, creada el 02/11/2026). |
| `catalogo-precios-sanson-2026-11.csv` | La misma lista estructurada: 31 productos con SKU, nombre, descripción técnica, precio mínimo y máximo, categoría y observaciones. |
| `carga-masiva-catalogo-sanson.csv` | El catálogo en el formato de la carga masiva de productos (HU-19), listo para subir desde **Productos → Carga masiva**. |

## Criterios usados al estructurar la lista

- **Precio:** la lista da rangos (por ejemplo, 290-320). En la carga masiva se usa el **precio mínimo**; el rango completo queda en `catalogo-precios-sanson-2026-11.csv`. Los precios están en soles.
- **Puntal de 4 m:** se registra el precio normal (S/ 66-67); el de preventa (S/ 62-64) queda en la descripción.
- **Stock:** la lista no trae cantidades, así que el stock va en **0**. El stock inicial se registra con el conteo físico de la fase F2 del plan de implantación.
- **Costo promedio:** la carga masiva inicia el costo promedio con el precio informado; la primera entrada de mercadería lo corrige con el costo real.
- **Categorías:** se usan las existentes (PLATAFORMA, BARANDA, ESCALERA, RUEDAS NYLON) y tres nuevas que crea `database/06-catalogo-categorias.sql`: ANDAMIOS, ACCESORIOS y PUNTALES Y ENCOFRADO.

## Por confirmar con la empresa

1. **BRL-DUO y BRL-JDUO:** la página 2 lista la "barandilla lateral (dúo)" a S/ 120 y la página 3 el "juego dúo de barandilla lateral" a S/ 230. Se registraron como dos productos.
2. **VIG-3:** la lista dice "Vigas de 3" (S/ 300), sin unidad ni material.
3. **ALI-1M:** hay alineadores de 0.5, 1.5, 2 y 3 m, pero solo se lista el precio del de 1 m.
4. **Plataformas y barandas:** un mismo rango de precio cubre varios modelos.

## Cómo cargarlo

```bash
# 1. Crear las categorías nuevas (una sola vez; es idempotente)
mysql -u root -p inventario_andamios < database/06-catalogo-categorias.sql
```

2. Ingresar como administrador → **Productos → Carga masiva** → subir `carga-masiva-catalogo-sanson.csv` en modo *todo o nada*.
3. Registrar el stock real de cada producto con un ajuste de inventario después del conteo físico.

En una instalación nueva con contenedores, el script `06` se aplica solo al crear la base.

-- =============================================================================
-- 06-catalogo-categorias.sql
-- Categorías que necesita el catálogo de la lista de precios de Andamios Sansón
-- (PRECIOS SANSON, noviembre de 2026; ver datos/README.md).
--
-- Solo agrega las categorías que faltan: los productos se cargan desde la
-- pantalla de carga masiva (HU-19) con datos/carga-masiva-catalogo-sanson.csv,
-- para que el stock inicial salga del conteo físico y no de este script.
--
-- Idempotente: puede ejecutarse varias veces sin duplicar filas.
-- =============================================================================

INSERT INTO `categorias` (`nombre`, `descripcion`, `activo`, `fecha_creacion`)
SELECT n.nombre, n.descripcion, 1, NOW()
  FROM (
        SELECT 'ANDAMIOS' AS nombre,
               'Juegos de andamio (Acrow, Reforzado, banquetero, MF48, liviano fusionado y pintado)' AS descripcion
        UNION ALL
        SELECT 'ACCESORIOS',
               'Crucetas, niveladores, adaptadores, rodapiés y alineadores'
        UNION ALL
        SELECT 'PUNTALES Y ENCOFRADO',
               'Puntales, vigas, espárragos y bolillos'
       ) n
 WHERE NOT EXISTS (
        SELECT 1 FROM `categorias` c WHERE UPPER(c.`nombre`) = UPPER(n.nombre)
       );

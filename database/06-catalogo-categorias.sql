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

-- Sin esta línea, el cliente de MySQL del contenedor lee el archivo como
-- latin1 y las tildes quedan dañadas ("rodapiÃ©s" en lugar de "rodapiés").
SET NAMES utf8mb4;

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

-- Repara las descripciones si una ejecución anterior las guardó sin UTF-8.
UPDATE `categorias`
   SET `descripcion` = 'Crucetas, niveladores, adaptadores, rodapiés y alineadores'
 WHERE `nombre` = 'ACCESORIOS' AND `descripcion` LIKE 'Crucetas,%';

UPDATE `categorias`
   SET `descripcion` = 'Puntales, vigas, espárragos y bolillos'
 WHERE `nombre` = 'PUNTALES Y ENCOFRADO' AND `descripcion` LIKE 'Puntales,%';

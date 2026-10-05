-- =====================================================================
-- Migracion del Sprint 2 - catalogo, movimientos y valorizacion
-- Sistema web para el control de inventario - Andamios Sanson, 2026
--
-- Lleva la base al esquema que exige el incremento del Sprint 2:
--   HU-11  Catalogo maestro de categorias y proveedores
--   HU-12  Entrada con actualizacion transaccional de stock
--   HU-13  Salida con validacion y bloqueo optimista (columna version)
--   HU-14  Motivo tipificado obligatorio en cada movimiento
--   HU-15  Anulacion de movimiento con reversion compensatoria
--   HU-16  Ajuste de inventario por conteo fisico con aprobacion
--   HU-17  Valorizacion por promedio ponderado
--   HU-18  Kardex por producto con saldo acumulado
--   HU-20  Umbrales de reposicion por producto
--
-- SE PUEDE EJECUTAR VARIAS VECES: cada cambio se aplica solo si falta, de
-- modo que tambien sirve para completar una ejecucion que quedo a medias.
--
-- Requiere que 02-sprint1-seguridad.sql ya se haya ejecutado.
--
-- MySQL confirma cada DDL automaticamente, asi que esto NO se deshace con
-- ROLLBACK. Respalde la base antes de ejecutarlo.
-- =====================================================================

-- Se fija la base explicitamente para no depender del esquema que este
-- seleccionado en MySQL Workbench.
USE `inventario_andamios`;

-- MySQL Workbench bloquea por defecto los UPDATE sin clave en el WHERE.
SET @safe_updates_previo = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ---------------------------------------------------------------------
-- Ayudantes: agregan una columna, un indice o una clave ajena solo si
-- todavia no existen
-- ---------------------------------------------------------------------

DROP PROCEDURE IF EXISTS sp_agregar_columna;
DROP PROCEDURE IF EXISTS sp_agregar_indice;
DROP PROCEDURE IF EXISTS sp_agregar_clave_ajena;

DELIMITER $$

CREATE PROCEDURE sp_agregar_columna(
    IN p_tabla VARCHAR(64),
    IN p_columna VARCHAR(64),
    IN p_definicion TEXT)
BEGIN
    DECLARE v_existe INT;

    SELECT COUNT(*) INTO v_existe
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabla
       AND COLUMN_NAME = p_columna;

    IF v_existe = 0 THEN
        SET @sentencia = CONCAT('ALTER TABLE `', p_tabla, '` ADD COLUMN `', p_columna, '` ', p_definicion);
        PREPARE st FROM @sentencia;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

CREATE PROCEDURE sp_agregar_indice(
    IN p_tabla VARCHAR(64),
    IN p_indice VARCHAR(64),
    IN p_columnas VARCHAR(255))
BEGIN
    DECLARE v_existe INT;

    SELECT COUNT(*) INTO v_existe
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabla
       AND INDEX_NAME = p_indice;

    IF v_existe = 0 THEN
        SET @sentencia = CONCAT('ALTER TABLE `', p_tabla, '` ADD INDEX `', p_indice, '` (', p_columnas, ')');
        PREPARE st FROM @sentencia;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

CREATE PROCEDURE sp_agregar_clave_ajena(
    IN p_tabla VARCHAR(64),
    IN p_restriccion VARCHAR(64),
    IN p_definicion TEXT)
BEGIN
    DECLARE v_existe INT;

    SELECT COUNT(*) INTO v_existe
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabla
       AND CONSTRAINT_NAME = p_restriccion
       AND CONSTRAINT_TYPE = 'FOREIGN KEY';

    IF v_existe = 0 THEN
        SET @sentencia = CONCAT('ALTER TABLE `', p_tabla, '` ADD CONSTRAINT `', p_restriccion, '` ', p_definicion);
        PREPARE st FROM @sentencia;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. Catalogo de motivos tipificados (HU-14)
-- ---------------------------------------------------------------------
-- La HU-14 exige que cada movimiento lleve un motivo de una lista cerrada.
-- Se modela como catalogo y no como enumeracion en el codigo para que el
-- administrador pueda dar de baja un motivo sin recompilar el sistema. La
-- columna `aplica_a` restringe cada motivo al tipo de movimiento que le
-- corresponde: un motivo de salida no puede usarse en una entrada.

CREATE TABLE IF NOT EXISTS `motivos_movimiento` (
  `codigo`      varchar(30)  NOT NULL,
  `nombre`      varchar(80)  NOT NULL,
  `aplica_a`    varchar(10)  NOT NULL,
  `exige_nota`  tinyint(1)   NOT NULL DEFAULT 0,
  `activo`      tinyint(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (`codigo`),
  KEY `idx_motivos_aplica_a` (`aplica_a`, `activo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Motivos iniciales acordados con el encargado de almacen. El INSERT es
-- idempotente: si el codigo ya existe se conserva lo que haya en la base,
-- porque el administrador puede haberlo editado desde el sistema.
INSERT INTO `motivos_movimiento` (`codigo`, `nombre`, `aplica_a`, `exige_nota`, `activo`) VALUES
  ('COMPRA',          'Compra a proveedor',                'ENTRADA', 0, 1),
  ('DEVOLUCION_OBRA', 'Devolucion de obra',                'ENTRADA', 0, 1),
  ('TRASLADO_ENTRADA','Traslado desde otro almacen',       'ENTRADA', 1, 1),
  ('AJUSTE_ENTRADA',  'Ajuste por conteo fisico (sobra)',  'ENTRADA', 1, 1),
  ('ALQUILER',        'Salida por alquiler a obra',        'SALIDA',  0, 1),
  ('VENTA',           'Venta',                             'SALIDA',  0, 1),
  ('TRASLADO_SALIDA', 'Traslado hacia otro almacen',       'SALIDA',  1, 1),
  ('MERMA',           'Merma, perdida o deterioro',        'SALIDA',  1, 1),
  ('AJUSTE_SALIDA',   'Ajuste por conteo fisico (falta)',  'SALIDA',  1, 1),
  ('ANULACION_ENTRADA','Anulacion de un movimiento de salida', 'ENTRADA', 0, 1),
  ('ANULACION_SALIDA', 'Anulacion de un movimiento de entrada','SALIDA',  0, 1)
ON DUPLICATE KEY UPDATE `codigo` = `codigo`;

-- Los dos motivos de anulacion los usa el sistema al generar el asiento
-- compensatorio de la HU-15. No se ofrecen al registrar un movimiento a mano:
-- el servicio los asigna, y el cliente los filtra de la lista.

-- ---------------------------------------------------------------------
-- 2. Catalogo maestro: categorias y proveedores (HU-11)
-- ---------------------------------------------------------------------
-- El esquema original guardaba solo el nombre. La HU-11 pide un catalogo
-- administrable, con baja logica para no perder el historico de productos
-- que ya referencian una categoria o un proveedor retirado.

ALTER TABLE `categorias`
  MODIFY `nombre` varchar(60) NOT NULL;

CALL sp_agregar_columna('categorias', 'descripcion',    'varchar(200) DEFAULT NULL AFTER `nombre`');
CALL sp_agregar_columna('categorias', 'activo',         'tinyint(1) NOT NULL DEFAULT 1 AFTER `descripcion`');
CALL sp_agregar_columna('categorias', 'fecha_creacion', 'datetime DEFAULT NULL AFTER `activo`');

ALTER TABLE `proveedores`
  MODIFY `nombre` varchar(100) NOT NULL,
  MODIFY `ruc`    varchar(11)  DEFAULT NULL;

CALL sp_agregar_columna('proveedores', 'activo',         'tinyint(1) NOT NULL DEFAULT 1 AFTER `correo`');
CALL sp_agregar_columna('proveedores', 'fecha_creacion', 'datetime DEFAULT NULL AFTER `activo`');

-- El nombre de la categoria y el RUC del proveedor no pueden repetirse.
-- Se usan los ayudantes porque sp_agregar_indice_unico no existe aqui.
DROP PROCEDURE IF EXISTS sp_agregar_indice_unico;
DELIMITER $$
CREATE PROCEDURE sp_agregar_indice_unico(
    IN p_tabla VARCHAR(64),
    IN p_indice VARCHAR(64),
    IN p_columnas VARCHAR(255))
BEGIN
    DECLARE v_existe INT;
    SELECT COUNT(*) INTO v_existe
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabla
       AND INDEX_NAME = p_indice;
    IF v_existe = 0 THEN
        SET @sentencia = CONCAT('ALTER TABLE `', p_tabla, '` ADD UNIQUE KEY `', p_indice, '` (', p_columnas, ')');
        PREPARE st FROM @sentencia;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

CALL sp_agregar_indice_unico('categorias',  'uk_categorias_nombre', '`nombre`');
CALL sp_agregar_indice_unico('proveedores', 'uk_proveedores_ruc',   '`ruc`');

UPDATE `categorias`  SET `fecha_creacion` = NOW() WHERE `id` > 0 AND `fecha_creacion` IS NULL;
UPDATE `proveedores` SET `fecha_creacion` = NOW() WHERE `id` > 0 AND `fecha_creacion` IS NULL;

-- ---------------------------------------------------------------------
-- 3. Productos: umbrales, costo promedio y bloqueo optimista
-- ---------------------------------------------------------------------
-- stock_minimo ya existia. La HU-20 agrega el punto de reposicion, que es
-- el nivel en el que se debe pedir, y el stock maximo, que acota la compra.
CALL sp_agregar_columna('productos', 'punto_reposicion', 'int DEFAULT NULL AFTER `stock_minimo`');
CALL sp_agregar_columna('productos', 'stock_maximo',     'int DEFAULT NULL AFTER `punto_reposicion`');

-- HU-17: el costo promedio ponderado se recalcula en cada entrada. Se
-- guarda en la fila del producto para no recorrer todo el kardex cada vez
-- que se pide la valorizacion.
CALL sp_agregar_columna('productos', 'costo_promedio', 'decimal(12,4) NOT NULL DEFAULT 0.0000 AFTER `precio`');

-- HU-13: control de concurrencia optimista. Hibernate incrementa `version`
-- en cada escritura y rechaza la que llegue con una version vencida, de
-- modo que dos salidas simultaneas no pueden dejar el stock en negativo.
CALL sp_agregar_columna('productos', 'version', 'bigint NOT NULL DEFAULT 0');

CALL sp_agregar_columna('productos', 'activo', 'tinyint(1) NOT NULL DEFAULT 1');

-- HU-21: la busqueda paginada filtra por nombre y por SKU.
CALL sp_agregar_indice('productos', 'idx_productos_nombre',    '`nombre`');
CALL sp_agregar_indice('productos', 'idx_productos_categoria', '`categoria_id`, `activo`');

-- El costo promedio arranca en el precio registrado, que es el unico dato
-- de costo disponible en la base heredada. A partir de la primera entrada
-- del Sprint 2 el valor ya se calcula con el promedio ponderado real.
UPDATE `productos`
   SET `costo_promedio` = COALESCE(`precio`, 0)
 WHERE `id` > 0
   AND `costo_promedio` = 0;

-- Umbral de reposicion por omision: el minimo que ya tuviera cargado.
UPDATE `productos`
   SET `punto_reposicion` = `stock_minimo`
 WHERE `id` > 0
   AND `punto_reposicion` IS NULL
   AND `stock_minimo` IS NOT NULL;

-- ---------------------------------------------------------------------
-- 4. Movimientos: motivo, estado, costo y saldo (HU-12 a HU-18)
-- ---------------------------------------------------------------------

-- HU-14: motivo obligatorio, tomado del catalogo.
CALL sp_agregar_columna('movimientos', 'motivo',      'varchar(30) DEFAULT NULL AFTER `tipo`');
CALL sp_agregar_columna('movimientos', 'observacion', 'varchar(200) DEFAULT NULL AFTER `motivo`');

-- HU-15: un movimiento no se borra, se anula. El estado distingue el
-- asiento vigente del anulado, y `movimiento_origen_id` enlaza el asiento
-- compensatorio con el que revierte, de modo que el kardex conserva las
-- dos filas y el saldo queda correcto.
CALL sp_agregar_columna('movimientos', 'estado',               "varchar(12) NOT NULL DEFAULT 'REGISTRADO'");
CALL sp_agregar_columna('movimientos', 'movimiento_origen_id', 'bigint DEFAULT NULL');
CALL sp_agregar_columna('movimientos', 'fecha_anulacion',      'datetime DEFAULT NULL');
CALL sp_agregar_columna('movimientos', 'usuario_anulacion_id', 'bigint DEFAULT NULL');

-- HU-17: costo unitario del asiento. En las entradas lo informa quien
-- registra; en las salidas se toma el costo promedio vigente del producto.
CALL sp_agregar_columna('movimientos', 'costo_unitario', 'decimal(12,4) NOT NULL DEFAULT 0.0000');

-- HU-18: saldo del producto despues de aplicar el asiento. Guardarlo
-- evita recalcular la suma acumulada en cada consulta del kardex.
CALL sp_agregar_columna('movimientos', 'saldo_resultante', 'int DEFAULT NULL');

CALL sp_agregar_indice('movimientos', 'idx_movimientos_producto_fecha', '`producto_id`, `fecha`');
CALL sp_agregar_indice('movimientos', 'idx_movimientos_estado',         '`estado`');
CALL sp_agregar_indice('movimientos', 'idx_movimientos_motivo',         '`motivo`');

CALL sp_agregar_clave_ajena('movimientos', 'fk_movimientos_motivo',
    'FOREIGN KEY (`motivo`) REFERENCES `motivos_movimiento` (`codigo`)');
CALL sp_agregar_clave_ajena('movimientos', 'fk_movimientos_origen',
    'FOREIGN KEY (`movimiento_origen_id`) REFERENCES `movimientos` (`id`)');
CALL sp_agregar_clave_ajena('movimientos', 'fk_movimientos_usuario_anulacion',
    'FOREIGN KEY (`usuario_anulacion_id`) REFERENCES `usuarios` (`id`)');

-- Los movimientos heredados no tienen motivo. Se les asigna el motivo
-- generico que corresponde a su tipo para que la columna pueda volverse
-- obligatoria sin perder el historico.
UPDATE `movimientos` SET `motivo` = 'COMPRA'   WHERE `id` > 0 AND `motivo` IS NULL AND `tipo` = 'ENTRADA';
UPDATE `movimientos` SET `motivo` = 'ALQUILER' WHERE `id` > 0 AND `motivo` IS NULL AND `tipo` = 'SALIDA';

-- El costo unitario heredado se toma del precio del producto, que es el
-- unico dato disponible. Queda registrado como aproximacion.
UPDATE `movimientos` m
  JOIN `productos` p ON p.`id` = m.`producto_id`
   SET m.`costo_unitario` = COALESCE(p.`precio`, 0)
 WHERE m.`id` > 0
   AND m.`costo_unitario` = 0;

-- ---------------------------------------------------------------------
-- 5. Auditoria: detalle de la accion
-- ---------------------------------------------------------------------
-- La tabla solo guardaba un texto de 255 caracteres en `accion`, que no
-- alcanza para dejar constancia de una anulacion o de un ajuste con su
-- justificacion. Se separa la accion, que es el codigo, del detalle, que es
-- la explicacion legible, y se indexa la fecha porque la consulta de
-- auditoria siempre ordena por ella.

CALL sp_agregar_columna('auditoria', 'detalle', 'varchar(500) DEFAULT NULL AFTER `accion`');
CALL sp_agregar_indice('auditoria', 'idx_auditoria_fecha', '`fecha`');

-- ---------------------------------------------------------------------
-- 6. Ajustes de inventario por conteo fisico (HU-16)
-- ---------------------------------------------------------------------
-- El encargado registra el conteo y el ajuste queda PENDIENTE. Solo el
-- administrador o el gerente lo aprueban, y la aprobacion es la que genera
-- el movimiento que corrige el stock. Asi el conteo no modifica el
-- inventario por si solo y queda la traza de quien autorizo la diferencia.

CREATE TABLE IF NOT EXISTS `ajustes_inventario` (
  `id`                   bigint       NOT NULL AUTO_INCREMENT,
  `producto_id`          bigint       NOT NULL,
  `stock_sistema`        int          NOT NULL,
  `stock_fisico`         int          NOT NULL,
  `diferencia`           int          NOT NULL,
  `motivo`               varchar(30)  DEFAULT NULL,
  `observacion`          varchar(200) DEFAULT NULL,
  `estado`               varchar(12)  NOT NULL DEFAULT 'PENDIENTE',
  `usuario_solicita_id`  bigint       NOT NULL,
  `usuario_aprueba_id`   bigint       DEFAULT NULL,
  `fecha_solicitud`      datetime     NOT NULL,
  `fecha_resolucion`     datetime     DEFAULT NULL,
  `motivo_rechazo`       varchar(200) DEFAULT NULL,
  `movimiento_id`        bigint       DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ajustes_estado` (`estado`),
  KEY `idx_ajustes_producto` (`producto_id`),
  CONSTRAINT `fk_ajustes_producto`  FOREIGN KEY (`producto_id`)         REFERENCES `productos` (`id`),
  CONSTRAINT `fk_ajustes_solicita`  FOREIGN KEY (`usuario_solicita_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT `fk_ajustes_aprueba`   FOREIGN KEY (`usuario_aprueba_id`)  REFERENCES `usuarios` (`id`),
  CONSTRAINT `fk_ajustes_movimiento` FOREIGN KEY (`movimiento_id`)      REFERENCES `movimientos` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------

DROP PROCEDURE IF EXISTS sp_agregar_columna;
DROP PROCEDURE IF EXISTS sp_agregar_indice;
DROP PROCEDURE IF EXISTS sp_agregar_indice_unico;
DROP PROCEDURE IF EXISTS sp_agregar_clave_ajena;

SET SQL_SAFE_UPDATES = @safe_updates_previo;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
-- SELECT codigo, nombre, aplica_a FROM motivos_movimiento ORDER BY aplica_a, codigo;
-- SELECT id, sku, nombre, stock, punto_reposicion, costo_promedio, version FROM productos;
-- SELECT id, tipo, motivo, estado, cantidad, costo_unitario, saldo_resultante FROM movimientos;
-- SELECT accion, detalle, fecha FROM auditoria ORDER BY fecha DESC LIMIT 10;
-- SHOW TABLES;   -- debe estar ajustes_inventario y motivos_movimiento

-- =====================================================================
-- Migracion del Sprint 3 - reportes, alertas, usuarios y bitacoras
-- Sistema web para el control de inventario - Andamios Sanson, 2026
--
-- Lleva la base al esquema que exige el incremento del Sprint 3:
--   HU-28  Valorizacion con corte a fecha: cada movimiento guarda el costo
--          promedio que dejo en el producto
--   HU-31  Desactivacion de cuentas con efecto inmediato sobre las sesiones
--   HU-32  Bitacora de accesos exitosos y fallidos
--   HU-33  Consulta de la bitacora de auditoria con filtros (indices)
--
-- SE PUEDE EJECUTAR VARIAS VECES: cada cambio se aplica solo si falta.
--
-- Requiere que 03-sprint2-movimientos.sql ya se haya ejecutado.
--
-- MySQL confirma cada DDL automaticamente, asi que esto NO se deshace con
-- ROLLBACK. Respalde la base antes de ejecutarlo.
-- =====================================================================

USE `inventario_andamios`;

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
-- 1. Costo promedio resultante de cada movimiento (HU-28)
-- ---------------------------------------------------------------------
-- Para valorizar el inventario a una fecha pasada hacen falta dos datos por
-- producto: cuanto habia y a que costo. La cantidad se reconstruye desde el
-- stock actual restando lo ocurrido despues del corte, igual que el kardex.
-- El costo no se puede reconstruir asi, porque el promedio ponderado depende
-- del orden de las entradas. Por eso cada movimiento guarda, desde ahora, el
-- costo promedio con el que dejo al producto, como ya guarda el saldo.
--
-- Los movimientos anteriores quedan en NULL: no hay forma confiable de saber
-- su costo. La valorizacion a fecha usa en ese caso el costo vigente y lo
-- marca como estimado, en lugar de inventar una cifra.

CALL sp_agregar_columna('movimientos', 'costo_promedio_resultante',
     'decimal(12,4) DEFAULT NULL AFTER `saldo_resultante`');

-- La valorizacion a fecha busca, por producto, el ultimo movimiento anterior
-- al corte: el indice compuesto evita recorrer todo el historico.
CALL sp_agregar_indice('movimientos', 'idx_movimientos_producto_fecha', '`producto_id`, `fecha`');

-- ---------------------------------------------------------------------
-- 2. Bitacora de accesos (HU-32)
-- ---------------------------------------------------------------------
-- Cada intento de inicio de sesion queda registrado, salga bien o mal. Se
-- guarda lo que el usuario escribio como identificador aunque no exista,
-- porque los intentos contra cuentas inexistentes son justamente los que
-- delatan un ataque. La contrasena nunca se guarda.

CREATE TABLE IF NOT EXISTS `accesos` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `fecha`         datetime     NOT NULL,
  `identificador` varchar(100) NOT NULL,
  `usuario_id`    bigint       DEFAULT NULL,
  `resultado`     varchar(12)  NOT NULL,
  `motivo`        varchar(60)  DEFAULT NULL,
  `ip`            varchar(45)  DEFAULT NULL,
  `agente`        varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_accesos_fecha` (`fecha`),
  KEY `idx_accesos_resultado_fecha` (`resultado`, `fecha`),
  CONSTRAINT `fk_accesos_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 3. Sesiones validas desde (HU-31)
-- ---------------------------------------------------------------------
-- Cuando el administrador desactiva una cuenta, sus sesiones abiertas deben
-- dejar de funcionar en ese momento y no cuando venza el token. El servidor
-- rechaza todo token emitido antes de esta fecha. La misma columna sirve
-- despues para cerrar las sesiones al cambiar o restablecer la contrasena.

CALL sp_agregar_columna('usuarios', 'sesiones_validas_desde', 'datetime DEFAULT NULL');

-- ---------------------------------------------------------------------
-- 4. Consulta de auditoria por usuario y fecha (HU-33)
-- ---------------------------------------------------------------------

CALL sp_agregar_indice('auditoria', 'idx_auditoria_usuario_fecha', '`usuario_id`, `fecha`');

-- ---------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------

DROP PROCEDURE IF EXISTS sp_agregar_columna;
DROP PROCEDURE IF EXISTS sp_agregar_indice;
DROP PROCEDURE IF EXISTS sp_agregar_clave_ajena;

SET SQL_SAFE_UPDATES = @safe_updates_previo;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
-- SHOW COLUMNS FROM movimientos LIKE 'costo_promedio_resultante';
-- SELECT resultado, COUNT(*) FROM accesos GROUP BY resultado;

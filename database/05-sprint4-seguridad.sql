-- =====================================================================
-- Migracion del Sprint 4 - endurecimiento de la seguridad
-- Sistema web para el control de inventario - Andamios Sanson, 2026
--
-- Lleva la base al esquema que exige el incremento del Sprint 4:
--   HU-35  Bloqueo temporal de la cuenta tras intentos fallidos
--   HU-36  Restablecimiento de contrasena por el administrador
--   HU-37  Cambio de contrasena con politica de complejidad
--
-- SE PUEDE EJECUTAR VARIAS VECES: cada cambio se aplica solo si falta.
--
-- Requiere que 04-sprint3-reportes-accesos.sql ya se haya ejecutado.
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
-- 1. Bloqueo por intentos fallidos (HU-35)
-- ---------------------------------------------------------------------
-- Se cuenta cada fallo consecutivo y, al llegar al limite, la cuenta queda
-- bloqueada hasta una hora determinada. Un acceso correcto reinicia el
-- contador. Se guarda una hora de fin y no un indicador, para que el bloqueo
-- se levante solo sin que nadie tenga que intervenir.

CALL sp_agregar_columna('usuarios', 'intentos_fallidos', 'int NOT NULL DEFAULT 0');
CALL sp_agregar_columna('usuarios', 'bloqueado_hasta', 'datetime DEFAULT NULL');

-- ---------------------------------------------------------------------
-- 2. Contrasena temporal y fecha del ultimo cambio (HU-36, HU-37)
-- ---------------------------------------------------------------------
-- Cuando el administrador restablece una contrasena, la que entrega es
-- temporal: el usuario debe cambiarla en su siguiente ingreso.

CALL sp_agregar_columna('usuarios', 'debe_cambiar_password', 'tinyint(1) NOT NULL DEFAULT 0');
CALL sp_agregar_columna('usuarios', 'fecha_cambio_password', 'datetime DEFAULT NULL');

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
-- SELECT nombre_usuario, intentos_fallidos, bloqueado_hasta, debe_cambiar_password FROM usuarios;

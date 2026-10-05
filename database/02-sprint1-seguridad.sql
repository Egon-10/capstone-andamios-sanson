-- =====================================================================
-- Migracion del Sprint 1 - seguridad y catalogo
-- Sistema web para el control de inventario - Andamios Sanson, 2026
--
-- Lleva la base al esquema que exige el incremento del Sprint 1:
--   HU-02  Modelo de datos con integridad referencial y auditoria base
--   HU-03  Tokens de renovacion de sesion con rotacion
--   HU-04  Contrasenas cifradas (columna ampliada para el hash BCrypt)
--   HU-08  Registro de tokens de acceso invalidados al cerrar sesion
--   HU-10  Codigo unico de producto (SKU)
--   HU-43  Campos del formulario de registro de usuario y turno (CAM-02)
--
-- SE PUEDE EJECUTAR VARIAS VECES: cada cambio se aplica solo si falta, de
-- modo que tambien sirve para completar una ejecucion que quedo a medias.
--
-- MySQL confirma cada DDL automaticamente, asi que esto NO se deshace con
-- ROLLBACK. Respalde la base antes de ejecutarlo.
-- =====================================================================

-- MySQL Workbench bloquea por defecto los UPDATE sin clave en el WHERE.
SET @safe_updates_previo = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ---------------------------------------------------------------------
-- Ayudantes: agregan una columna o un indice solo si todavia no existen
-- ---------------------------------------------------------------------

DROP PROCEDURE IF EXISTS sp_agregar_columna;
DROP PROCEDURE IF EXISTS sp_agregar_indice_unico;

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

-- ---------------------------------------------------------------------
-- 1. Usuarios: campos de la HU-43 y turno de trabajo (CAM-02)
-- ---------------------------------------------------------------------

-- La columna password pasa a 100 caracteres para alojar el hash BCrypt (60).
ALTER TABLE `usuarios`
  MODIFY `nombre`   varchar(60)  DEFAULT NULL,
  MODIFY `correo`   varchar(100) DEFAULT NULL,
  MODIFY `password` varchar(100) DEFAULT NULL;

CALL sp_agregar_columna('usuarios', 'apellidos',        'varchar(60) DEFAULT NULL AFTER `nombre`');
CALL sp_agregar_columna('usuarios', 'tipo_documento',   'varchar(20) DEFAULT NULL AFTER `apellidos`');
CALL sp_agregar_columna('usuarios', 'numero_documento', 'varchar(20) DEFAULT NULL AFTER `tipo_documento`');
CALL sp_agregar_columna('usuarios', 'telefono',         'varchar(15) DEFAULT NULL AFTER `correo`');
CALL sp_agregar_columna('usuarios', 'nombre_usuario',   'varchar(30) DEFAULT NULL AFTER `telefono`');
CALL sp_agregar_columna('usuarios', 'area',             'varchar(20) DEFAULT NULL AFTER `password`');
CALL sp_agregar_columna('usuarios', 'turno',            'varchar(10) DEFAULT NULL AFTER `area`');
CALL sp_agregar_columna('usuarios', 'estado',           'varchar(10) DEFAULT NULL AFTER `turno`');
CALL sp_agregar_columna('usuarios', 'fecha_creacion',   'datetime DEFAULT NULL AFTER `estado`');

-- Un correo, un documento y un nombre de usuario no pueden repetirse.
-- En MySQL un indice UNIQUE admite varios NULL, de modo que las filas aun
-- sin completar no bloquean la restriccion.
CALL sp_agregar_indice_unico('usuarios', 'uk_usuarios_correo',           '`correo`');
CALL sp_agregar_indice_unico('usuarios', 'uk_usuarios_numero_documento', '`numero_documento`');
CALL sp_agregar_indice_unico('usuarios', 'uk_usuarios_nombre_usuario',   '`nombre_usuario`');

-- Toda cuenta existente queda activa. El nombre de usuario se deriva de la
-- parte local del correo; si eso genera un duplicado, la cuenta se deja sin
-- nombre de usuario y el equipo lo asigna a mano desde el sistema.
UPDATE `usuarios`
   SET `estado` = 'ACTIVO'
 WHERE `id` > 0
   AND `estado` IS NULL;

UPDATE `usuarios` u
  JOIN (
        SELECT `id`, SUBSTRING_INDEX(`correo`, '@', 1) AS candidato
          FROM `usuarios`
         WHERE `nombre_usuario` IS NULL
           AND `correo` IS NOT NULL
       ) d ON d.`id` = u.`id`
   SET u.`nombre_usuario` = d.candidato
 WHERE NOT EXISTS (
        SELECT 1 FROM (SELECT `id`, `nombre_usuario` FROM `usuarios`) x
         WHERE x.`nombre_usuario` = d.candidato
           AND x.`id` <> u.`id`
       );

-- ---------------------------------------------------------------------
-- 2. Productos: codigo unico SKU (HU-10, RF-10)
-- ---------------------------------------------------------------------

ALTER TABLE `productos`
  MODIFY `nombre`      varchar(100) DEFAULT NULL,
  MODIFY `descripcion` varchar(250) DEFAULT NULL;

CALL sp_agregar_columna('productos', 'sku', 'varchar(20) DEFAULT NULL AFTER `id`');
CALL sp_agregar_indice_unico('productos', 'uk_productos_sku', '`sku`');

-- ATENCION: estos codigos son PROVISIONALES. El analisis causal (Tabla 4,
-- causa "Maquinaria / Tecnologia") identifico la falta de un catalogo con
-- codigo unico como una de las causas del problema, por lo que deben
-- reemplazarse por la codificacion que acuerde la empresa antes de la
-- medicion posterior.
UPDATE `productos`
   SET `sku` = CONCAT('PROV-', LPAD(`id`, 3, '0'))
 WHERE `id` > 0
   AND `sku` IS NULL;

-- ---------------------------------------------------------------------
-- 3. Tokens de renovacion de sesion (HU-03, HU-09)
-- ---------------------------------------------------------------------

-- Solo se almacena el hash SHA-256 del token, nunca su valor en claro.
CREATE TABLE IF NOT EXISTS `refresh_tokens` (
  `id`             bigint(20)   NOT NULL AUTO_INCREMENT,
  `token_hash`     varchar(64)  NOT NULL,
  `usuario_id`     bigint(20)   NOT NULL,
  `expiracion`     datetime(6)  NOT NULL,
  `revocado`       bit(1)       NOT NULL DEFAULT b'0',
  `fecha_creacion` datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_tokens_hash` (`token_hash`),
  KEY `idx_refresh_tokens_usuario` (`usuario_id`),
  CONSTRAINT `fk_refresh_tokens_usuario`
    FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 4. Tokens de acceso invalidados al cerrar sesion (HU-08)
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tokens_revocados` (
  `jti`        varchar(64) NOT NULL,
  `expiracion` datetime(6) NOT NULL,
  PRIMARY KEY (`jti`),
  KEY `idx_tokens_revocados_expiracion` (`expiracion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- Limpieza
-- ---------------------------------------------------------------------

DROP PROCEDURE IF EXISTS sp_agregar_columna;
DROP PROCEDURE IF EXISTS sp_agregar_indice_unico;

SET SQL_SAFE_UPDATES = @safe_updates_previo;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
-- SELECT id, nombre_usuario, correo, estado, CHAR_LENGTH(password) AS largo
--   FROM usuarios;           -- largo = 60 cuando la clave ya esta cifrada
-- SELECT id, sku, nombre, stock FROM productos;
-- SHOW TABLES;               -- deben estar refresh_tokens y tokens_revocados

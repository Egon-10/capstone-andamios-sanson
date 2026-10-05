-- =====================================================================
-- Migracion del Sprint 1 - seguridad y catalogo
-- Sistema web para el control de inventario - Andamios Sanson, 2026
--
-- Lleva la base entregada por la empresa (01-inventario_andamios.sql) al
-- esquema que exige el incremento del Sprint 1:
--   HU-02  Modelo de datos con integridad referencial y auditoria base
--   HU-03  Tokens de renovacion de sesion con rotacion
--   HU-04  Contrasenas cifradas (columna ampliada para el hash BCrypt)
--   HU-08  Registro de tokens de acceso invalidados al cerrar sesion
--   HU-10  Codigo unico de producto (SKU)
--   HU-43  Campos del formulario de registro de usuario y turno (CAM-02)
--
-- Ejecutar UNA sola vez, despues de 01-inventario_andamios.sql.
-- Respalde la base antes de ejecutarlo.
-- =====================================================================

USE `inventario_andamios`;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 1. Usuarios: campos de la HU-43 y turno de trabajo (CAM-02)
-- ---------------------------------------------------------------------

-- La columna password pasa a 100 caracteres para alojar el hash BCrypt (60).
ALTER TABLE `usuarios`
  MODIFY `nombre`   varchar(60)  DEFAULT NULL,
  MODIFY `correo`   varchar(100) DEFAULT NULL,
  MODIFY `password` varchar(100) DEFAULT NULL;

ALTER TABLE `usuarios`
  ADD COLUMN `apellidos`         varchar(60) DEFAULT NULL AFTER `nombre`,
  ADD COLUMN `tipo_documento`    varchar(20) DEFAULT NULL AFTER `apellidos`,
  ADD COLUMN `numero_documento`  varchar(20) DEFAULT NULL AFTER `tipo_documento`,
  ADD COLUMN `telefono`          varchar(15) DEFAULT NULL AFTER `correo`,
  ADD COLUMN `nombre_usuario`    varchar(30) DEFAULT NULL AFTER `telefono`,
  ADD COLUMN `area`              varchar(20) DEFAULT NULL AFTER `password`,
  ADD COLUMN `turno`             varchar(10) DEFAULT NULL AFTER `area`,
  ADD COLUMN `estado`            varchar(10) DEFAULT NULL AFTER `turno`,
  ADD COLUMN `fecha_creacion`    datetime    DEFAULT NULL AFTER `estado`;

-- Un correo, un documento y un nombre de usuario no pueden repetirse.
-- En MySQL y MariaDB un indice UNIQUE admite varios NULL, de modo que las
-- filas aun sin completar no bloquean la restriccion.
ALTER TABLE `usuarios`
  ADD UNIQUE KEY `uk_usuarios_correo` (`correo`),
  ADD UNIQUE KEY `uk_usuarios_numero_documento` (`numero_documento`),
  ADD UNIQUE KEY `uk_usuarios_nombre_usuario` (`nombre_usuario`);

-- Las tres cuentas existentes quedan activas y reciben un nombre de usuario
-- derivado de su correo. Los demas campos (apellidos, documento, telefono,
-- area, turno) los completa el equipo con los datos reales de la empresa.
UPDATE `usuarios` SET `estado` = 'ACTIVO' WHERE `estado` IS NULL;
UPDATE `usuarios` SET `nombre_usuario` = 'admin'     WHERE `id` = 1 AND `nombre_usuario` IS NULL;
UPDATE `usuarios` SET `nombre_usuario` = 'encargado' WHERE `id` = 2 AND `nombre_usuario` IS NULL;
UPDATE `usuarios` SET `nombre_usuario` = 'gerente'   WHERE `id` = 3 AND `nombre_usuario` IS NULL;

-- ---------------------------------------------------------------------
-- 2. Productos: codigo unico SKU (HU-10, RF-10)
-- ---------------------------------------------------------------------

ALTER TABLE `productos`
  MODIFY `nombre`      varchar(100) DEFAULT NULL,
  MODIFY `descripcion` varchar(250) DEFAULT NULL,
  ADD COLUMN `sku` varchar(20) DEFAULT NULL AFTER `id`,
  ADD UNIQUE KEY `uk_productos_sku` (`sku`);

-- ATENCION: los codigos siguientes son PROVISIONALES. El analisis causal
-- (Tabla 4, causa "Maquinaria / Tecnologia") identifico la falta de un
-- catalogo con codigo unico como una de las causas del problema, por lo que
-- estos valores deben reemplazarse por la codificacion que acuerde la
-- empresa antes de la medicion posterior.
UPDATE `productos` SET `sku` = 'PROV-001' WHERE `id` = 1 AND `sku` IS NULL;
UPDATE `productos` SET `sku` = 'PROV-002' WHERE `id` = 2 AND `sku` IS NULL;
UPDATE `productos` SET `sku` = 'PROV-003' WHERE `id` = 3 AND `sku` IS NULL;
UPDATE `productos` SET `sku` = 'PROV-004' WHERE `id` = 4 AND `sku` IS NULL;
UPDATE `productos` SET `sku` = 'PROV-005' WHERE `id` = 5 AND `sku` IS NULL;

-- ---------------------------------------------------------------------
-- 3. Tokens de renovacion de sesion (HU-03, HU-09)
-- ---------------------------------------------------------------------

-- Solo se almacena el hash SHA-256 del token, nunca su valor en claro.
CREATE TABLE `refresh_tokens` (
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

CREATE TABLE `tokens_revocados` (
  `jti`        varchar(64) NOT NULL,
  `expiracion` datetime(6) NOT NULL,
  PRIMARY KEY (`jti`),
  KEY `idx_tokens_revocados_expiracion` (`expiracion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

COMMIT;

-- ---------------------------------------------------------------------
-- Verificacion
-- ---------------------------------------------------------------------
-- SELECT id, nombre_usuario, correo, estado, LENGTH(password) AS largo_hash
--   FROM usuarios;            -- largo_hash debe ser 60 (BCrypt)
-- SELECT id, sku, nombre, stock, stock_minimo FROM productos;
-- SHOW TABLES;                -- deben aparecer refresh_tokens y tokens_revocados

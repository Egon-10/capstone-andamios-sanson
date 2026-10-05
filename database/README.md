# Base de datos — `inventario_andamios`

Scripts de la base de datos del sistema web de control de inventario de
**Andamios Sansón**. Se ejecutan en orden.

| Archivo | Qué contiene |
|---|---|
| `01-inventario_andamios.sql` | Volcado entregado por la empresa (phpMyAdmin / MariaDB 10.4, 28-06-2026): estructura, catálogo de productos, proveedores, categorías, roles, usuarios y movimientos registrados. |
| `02-sprint1-seguridad.sql` | Migración del Sprint 1: campos de la HU-43, SKU de productos, tablas de sesión y restricciones de unicidad. |
| `03-sprint2-movimientos.sql` | Migración del Sprint 2: catálogo de motivos tipificados, estado y costo de los movimientos, saldo del kardex, costo promedio y umbrales de los productos, columna de versión para el bloqueo optimista, tabla de ajustes de inventario y detalle de auditoría. |

## Cómo cargarla en local

```bash
# 1. Crear la base (si aún no existe)
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS inventario_andamios \
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2. Cargar el volcado de la empresa
mysql -u root -p inventario_andamios < database/01-inventario_andamios.sql

# 3. Aplicar la migración del Sprint 1
mysql -u root -p inventario_andamios < database/02-sprint1-seguridad.sql

# 3. Migración del Sprint 2
mysql -u root -p inventario_andamios < database/03-sprint2-movimientos.sql
```

En Windows con XAMPP, el ejecutable suele estar en
`C:\xampp\mysql\bin\mysql.exe`. También puede importar ambos archivos desde
phpMyAdmin, **en ese orden**.

El script `02` se ejecuta **una sola vez**: vuelve a correrlo falla porque las
columnas y tablas ya existirían. Respalde la base antes de aplicarlo.

## Cuentas de desarrollo

> ⚠️ El volcado original traía las contraseñas en **texto plano** (`123456`).
> Como este repositorio es público, se reemplazaron por hashes BCrypt
> (control SEG-BD-01). **Las contraseñas reales de la empresa deben cambiarse
> también en su sistema**: `123456` no cumple la política de complejidad
> definida en la HU-43.

| Correo | Usuario | Contraseña | Rol |
|---|---|---|---|
| `admin@gmail.com` | `admin` | `Andamios2026` | ADMINISTRADOR |
| `encargado@gmail.com` | `encargado` | `Encargado2026` | ENCARGADO |
| `gerente@gmail.com` | `gerente` | `Gerente2026` | GERENTE |

Son credenciales **solo para el entorno de desarrollo**. Antes del despliegue
hay que darlas de baja o cambiarlas desde la pantalla de gestión de usuarios.

## Datos pendientes de completar

El volcado es anterior al Sprint 1, así que hay campos nuevos que quedaron
vacíos y que el equipo debe llenar con información real de la empresa:

- **Usuarios**: `apellidos`, `tipo_documento`, `numero_documento`, `telefono`,
  `area` y `turno`. El script solo deriva el `nombre_usuario` del correo y
  marca las tres cuentas como `ACTIVO`.
- **Productos**: los SKU quedaron como `PROV-001` … `PROV-005`. Son
  **provisionales**: deben reemplazarse por la codificación que acuerde la
  empresa, ya que el catálogo con código único es justamente una de las causas
  raíz identificadas en el diagnóstico.

## Qué pasa al arrancar el backend

`DataInitializer` se ejecuta en cada arranque y:

1. Crea los roles `ADMINISTRADOR`, `GERENTE` y `ENCARGADO` si faltan.
2. Cifra con BCrypt cualquier contraseña que siga en texto plano.
3. Crea un administrador inicial solo si la tabla `usuarios` está vacía y se
   configuraron `app.admin.correo` y `app.admin.password`.

Gracias al paso 2, el sistema sigue funcionando aunque alguien cargue un
volcado antiguo: las contraseñas se migran solas en el primer arranque.

# Despliegue del sistema de inventario (HU-42)

Guía para instalar, actualizar, respaldar y restaurar el sistema en el servidor
de la empresa. El mismo procedimiento se ejecuta en la integración continua en
cada cambio («despliegue preliminar»), de modo que la guía está probada.

## 1. Requisitos del servidor

| Recurso | Mínimo | Recomendado |
|---|---|---|
| CPU | 2 vCPU | 2 vCPU |
| Memoria | 2 GB | 4 GB |
| Disco | 20 GB | 40 GB (respaldos) |
| Sistema | Ubuntu 22.04 o 24.04 | — |
| Software | Docker Engine 24+ con el complemento `docker compose` | — |

Solo se publica un puerto (por omisión el 8080). La base de datos y el backend
quedan en una red interna de Docker y no son accesibles desde fuera.

## 2. Primera instalación

```bash
git clone https://github.com/Egon-10/capstone-andamios-sanson.git
cd capstone-andamios-sanson
cp .env.example .env
```

Completar `.env` con valores aleatorios (nunca reutilizar los de otra instalación):

```bash
openssl rand -base64 24   # MYSQL_ROOT_PASSWORD y MYSQL_PASSWORD
openssl rand -base64 48   # JWT_SECRET
chmod 600 .env
```

Levantar el sistema:

```bash
docker compose up -d --build
docker compose ps          # los tres servicios deben figurar como "healthy"
```

En el primer arranque MySQL carga `database/01-inventario_andamios.sql` y aplica
las migraciones `02` a `05` en orden. Luego el sistema queda en
`http://<servidor>:8080`.

### Contraseñas de las cuentas iniciales

El volcado de la empresa trae tres cuentas (`admin`, `gerente`, `encargado`).
Antes de entregar el sistema, el administrador debe:

1. Ingresar con `admin` y cambiar su contraseña en **Mi perfil → Cambiar contraseña**.
2. En **Usuarios**, usar **Restablecer contraseña** en `gerente` y `encargado`:
   el sistema genera una contraseña temporal que cada persona debe cambiar en
   su primer ingreso (HU-36).

### HTTPS

En producción el sistema debe publicarse con HTTPS. La opción más simple es un
proxy con certificado delante del puerto 8080 (por ejemplo Caddy o el balanceador
del proveedor del VPS). La API ya envía `Strict-Transport-Security`, que el
navegador aplica en cuanto la conexión es HTTPS.

## 3. Actualización a una versión nueva

```bash
cd capstone-andamios-sanson
./respaldo.sh                      # ver sección 4, siempre antes de actualizar
git pull
docker compose up -d --build
docker compose ps
```

Si la versión trae una migración nueva (`database/06-...sql`), aplicarla sobre la
base existente, porque los scripts de `database/` solo se ejecutan solos en el
primer arranque:

```bash
docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < database/06-....sql
```

Todas las migraciones se pueden ejecutar más de una vez sin efectos (son
idempotentes; la integración continua lo comprueba en cada cambio).

## 4. Respaldo y restauración

Respaldo diario (programar con `cron`, por ejemplo a las 23:30):

```bash
mkdir -p respaldos
docker compose exec -T db sh -c \
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines inventario_andamios' \
  | gzip > respaldos/inventario-$(date +%F).sql.gz
find respaldos -name '*.sql.gz' -mtime +30 -delete    # conservar 30 días
```

Restauración:

```bash
gunzip -c respaldos/inventario-AAAA-MM-DD.sql.gz | \
  docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" inventario_andamios'
```

Los respaldos contienen datos personales y económicos: guardarlos fuera del
servidor y con acceso restringido.

## 5. Operación y monitoreo (HU-41)

| Necesidad | Comando |
|---|---|
| Estado de los servicios | `docker compose ps` |
| Registro del backend | `docker compose logs -f backend` |
| Seguir una solicitud | buscar su identificador: `docker compose logs backend \| grep <id>` |
| Solicitudes lentas (> 1 s) | `docker compose logs backend \| grep "(lenta)"` |

Cada línea del registro lleva el identificador de la solicitud (`sol=`) y el
usuario que la originó (`usr=`). El mismo identificador vuelve al navegador en
la cabecera `X-Request-Id` y en el cuerpo de cualquier error, para que el
usuario pueda informarlo.

## 6. Reversión

Si una versión nueva falla:

```bash
git checkout <etiqueta-o-commit-anterior>
docker compose up -d --build
```

Si la versión fallida aplicó una migración, restaurar el respaldo tomado antes
de actualizar (sección 4).

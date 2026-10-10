# Implantación en la red local de la empresa

El sistema se instala en **una PC de la empresa** que hace de servidor. El
personal entra desde el navegador de cualquier computadora o celular
**conectado a la red (cable o wifi) de la empresa**. Desde internet no es
accesible: no hay dominio, ni puertos abiertos en el router, ni costo de
servidor.

Tiempo estimado: 45 a 60 minutos.

```
  Celulares y PCs del personal ──wifi / cable──▶  PC servidor (Docker)
  http://192.168.1.50:8080                         ├─ frontend (Nginx)  :8080
                                                   ├─ backend  (API)    solo red interna
                                                   └─ db       (MySQL)  solo red interna
```

---

## 1. Elegir la PC servidor

| Requisito | Mínimo |
|---|---|
| Sistema | Windows 10/11 de 64 bits (Pro o Home) o Ubuntu 22.04/24.04 |
| Memoria | 8 GB (el sistema usa unos 2 GB) |
| Disco libre | 20 GB |
| Red | Conectada al router **por cable** de preferencia |
| Uso | Debe quedar **encendida** en el horario de trabajo del almacén |

Conviene una PC de oficina que no se apague ni se use para otras tareas
pesadas. Configure que **no se suspenda**:
Configuración → Sistema → Inicio/apagado y suspensión → *Suspender: Nunca*.

---

## 2. Fijar la dirección IP de la PC servidor

El personal entrará con una dirección como `http://192.168.1.50:8080`; esa
dirección no debe cambiar.

1. En la PC servidor, abra **cmd** y ejecute `ipconfig`. Anote la
   *Dirección IPv4* (por ejemplo `192.168.1.50`) y la *Puerta de enlace*
   (por ejemplo `192.168.1.1`, que es el router).
2. Entre al router desde el navegador (`http://192.168.1.1`) con la cuenta
   del router y busque **DHCP → Reserva de direcciones** (o *Address
   Reservation* / *IP estática*). Reserve la IP anotada para esa PC.
   Si no tiene acceso al router, pídaselo a quien administra el internet de
   la empresa.
3. Compruebe en el router que **no** exista ninguna regla de *reenvío de
   puertos* (*port forwarding*) ni *DMZ* hacia esa PC. Así el sistema queda
   solo para la red local.

---

## 3. Instalar Docker

**Windows:**

1. Descargue e instale **Docker Desktop** desde
   <https://www.docker.com/products/docker-desktop/> (aceptar la opción
   *Use WSL 2*). Reinicie si lo pide. Docker Desktop es gratuito para
   empresas de menos de 250 trabajadores y menos de US$10 millones de
   ingresos anuales.
2. Abra Docker Desktop → **Settings → General** → marque
   *Start Docker Desktop when you sign in*.
3. Instale **Git for Windows**: <https://git-scm.com/download/win>.

**Ubuntu:**

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # cerrar sesión y volver a entrar
```

---

## 4. Descargar el sistema

En **PowerShell** (Windows) o una terminal (Ubuntu):

```powershell
cd $HOME
git clone https://github.com/Egon-10/capstone-andamios-sanson.git
cd capstone-andamios-sanson
# Mientras los pull requests no estén integrados, use la rama del Sprint 4.
# Después de integrarlos, use main.
git checkout feature/sprint-4-seguridad-despliegue
```

---

## 5. Configuración (`.env`)

Cree el archivo `.env` en la carpeta del proyecto. **Reemplace `192.168.1.50`
por la IP del paso 2.** Las contraseñas deben ser largas y aleatorias:

**Windows (PowerShell):**

```powershell
$IP = '192.168.1.50'
function Azar($n) { -join ((48..57)+(65..90)+(97..122) | Get-Random -Count $n | ForEach-Object {[char]$_}) }
@"
MYSQL_ROOT_PASSWORD=$(Azar 32)
MYSQL_USER=inventario
MYSQL_PASSWORD=$(Azar 32)
JWT_SECRET=$(Azar 60)
PUERTO_WEB=8080
CORS_ORIGENES=http://${IP}:8080
"@ | Set-Content -Encoding ascii .env
```

**Ubuntu:**

```bash
IP=192.168.1.50
cat > .env <<FIN
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
MYSQL_USER=inventario
MYSQL_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
PUERTO_WEB=8080
CORS_ORIGENES=http://${IP}:8080
FIN
chmod 600 .env
```

`CORS_ORIGENES` debe ser exactamente la dirección con la que el personal abre
el sistema. El archivo `.env` contiene las claves de la base: **no lo copie,
no lo envíe por WhatsApp ni lo suba a GitHub** (ya está excluido).

---

## 6. Levantar el sistema

```powershell
docker compose up -d --build
docker compose ps
```

La primera vez tarda entre 5 y 10 minutos. Los tres servicios (`db`,
`backend`, `frontend`) deben figurar como **healthy**. Gracias a
`restart: unless-stopped`, se vuelven a levantar solos cuando la PC se
reinicia (con Docker Desktop configurado para iniciar con la sesión).

**Permitir el acceso desde la red (solo Windows).** En PowerShell **como
administrador**:

```powershell
New-NetFirewallRule -DisplayName "Inventario Sansón (red local)" `
  -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow `
  -Profile Private -RemoteAddress LocalSubnet
```

La regla solo acepta equipos de la misma red local. Además, en
Configuración → Red e Internet, la red de la empresa debe estar marcada como
**Privada**.

**Probar:** desde un celular conectado al wifi de la empresa, abra
`http://192.168.1.50:8080`. Debe aparecer la pantalla de inicio de sesión.
Desde los datos móviles (sin wifi) **no** debe abrir: eso confirma que solo
funciona en la red local.

---

## 7. Primer acceso del administrador

La base trae las cuentas del volcado de la empresa. Para darle al
administrador una contraseña temporal (el sistema le obligará a cambiarla al
entrar):

```powershell
# 1. Ver las cuentas y su rol (1 = administrador, 2 = encargado, 3 = gerente)
"SELECT id, nombre_usuario, rol_id, estado FROM usuarios;" |
  docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" inventario_andamios'

# 2. Contraseña temporal (10+ caracteres con mayúscula, minúscula, número y
#    símbolo) y el usuario del administrador visto en el paso 1
$TEMPORAL = 'Escriba#Aqui2026'
$ADMIN    = 'NOMBRE_DEL_ADMIN'

# 3. Generar su hash BCrypt y aplicarlo
$HASH = (docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 u $TEMPORAL).Trim().Substring(2)
"UPDATE usuarios SET password='$HASH', debe_cambiar_password=1, intentos_fallidos=0, bloqueado_hasta=NULL WHERE nombre_usuario='$ADMIN';" |
  docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" inventario_andamios'
```

En Ubuntu son los mismos pasos con `echo "..." |` en lugar de las comillas de
PowerShell.

Entre con ese usuario y la temporal: el sistema pedirá la contraseña
definitiva. Luego, en **Usuarios**, el administrador crea las cuentas del
personal o les genera contraseñas temporales con **Restablecer contraseña**.

---

## 8. Respaldo diario

**Windows:** pruebe primero a mano:

```powershell
powershell -ExecutionPolicy Bypass -File .\respaldo.ps1
```

Prográmelo en el **Programador de tareas**: *Crear tarea básica* →
*Diariamente* a las 23:30 → *Iniciar un programa*:

- Programa: `powershell`
- Argumentos: `-ExecutionPolicy Bypass -File "C:\Users\USUARIO\capstone-andamios-sanson\respaldo.ps1"`

**Ubuntu:** `./respaldo.sh` y la línea de `crontab` de `docs/despliegue.md`.

Los respaldos quedan en la carpeta `respaldos` (30 días). **Copie el más
reciente una vez por semana a un USB o a la nube de la empresa**: si la PC
servidor se malogra, ese archivo es el inventario.

**Restaurar** (por ejemplo, en una PC nueva con el sistema ya levantado):

```powershell
docker compose cp .\respaldos\inventario-AAAA-MM-DD-HHMM.sql.gz db:/tmp/r.sql.gz
docker compose exec db sh -c 'gunzip -c /tmp/r.sql.gz | mysql -uroot -p"$MYSQL_ROOT_PASSWORD" inventario_andamios'
```

---

## 9. Dar acceso al personal

- Comparta la dirección `http://192.168.1.50:8080` como **marcador** en las
  PCs y como **acceso directo en la pantalla de inicio** de los celulares
  (en Chrome: menú ⋮ → *Agregar a pantalla principal*). Un código QR impreso
  en el almacén con esa dirección facilita el primer ingreso.
- Cada persona usa **su propia cuenta**; no se comparten contraseñas.
- El wifi de la empresa debe tener contraseña WPA2/WPA3 y, si hay wifi para
  visitas, que sea una red separada.

---

## 10. Actualizar a una nueva versión

```powershell
cd $HOME\capstone-andamios-sanson
powershell -ExecutionPolicy Bypass -File .\respaldo.ps1
git pull
docker compose up -d --build
docker compose ps
```

---

## 11. Seguridad en la red local

| Aspecto | Cómo queda |
|---|---|
| Acceso desde internet | No existe: sin dominio, sin reenvío de puertos, firewall limitado a la subred local |
| Base de datos y API | Solo accesibles dentro de Docker; solo se publica el puerto 8080 |
| Cuentas | Inicio de sesión, roles, bloqueo tras 5 intentos, contraseñas temporales y auditoría (igual que antes) |
| Cifrado (HTTPS) | La comunicación dentro de la red local viaja sin cifrar. Se mitiga con wifi protegido y red separada para visitas. Es un riesgo aceptado, registrado en el informe |

---

## 12. Evidencias para el informe

1. `ipconfig` y la reserva de IP en el router.
2. `docker compose ps` con los servicios sanos.
3. El sistema abierto desde un celular en el wifi de la empresa.
4. El mismo celular con datos móviles sin poder abrirlo (prueba de que es solo red local).
5. El primer ingreso del administrador (cambio obligatorio de contraseña).
6. El personal usando el sistema en el almacén (con su consentimiento).
7. La carpeta `respaldos` después de la primera noche.

---

## Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| Desde otro equipo no abre | Firewall de Windows o red marcada como *Pública* | Paso 6: regla de firewall y red *Privada* |
| Abre pero no deja iniciar sesión (error de origen) | `CORS_ORIGENES` no coincide con la dirección usada | Corregir `.env` y `docker compose up -d` |
| Dejó de abrir otro día | Cambió la IP de la PC | Reservar la IP en el router (paso 2) y actualizar `.env` |
| Nada funciona tras reiniciar la PC | Docker Desktop no arrancó | Abrirlo y marcar *Start when you sign in* |
| `backend` no llega a *healthy* | Poca memoria libre | Cerrar programas pesados o usar una PC con más memoria |

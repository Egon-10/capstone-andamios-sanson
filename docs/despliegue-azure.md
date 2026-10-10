# Implantación en Azure (Azure for Students)

Guía paso a paso para poner el sistema en producción en una máquina virtual de
Azure con el crédito de estudiante, con HTTPS y respaldo diario. Usa el mismo
`docker-compose.yml` que se prueba en cada pull request; lo único que se agrega
es `docker-compose.https.yml`, que obtiene el certificado automáticamente.

Tiempo estimado: 45 a 60 minutos.

---

## 0. Antes de empezar: costo

El crédito de Azure for Students es de US$100 por 12 meses y no pide tarjeta.
El sistema necesita al menos 2 GB de memoria (MySQL + Java + Nginx):

| Tamaño | vCPU / RAM | Uso recomendado |
|---|---|---|
| **B1ms** | 1 / 2 GB | Suficiente con la memoria de intercambio del paso 3. Recomendado. |
| B2s | 2 / 4 GB | Más holgado; consume el crédito unas dos veces más rápido. |
| B1s | 1 / 1 GB | Gratis 750 h/mes el primer año, pero **no alcanza** para este sistema. |

Revise el precio vigente en la [calculadora de Azure](https://azure.microsoft.com/pricing/calculator/)
para la región que elija. Al crear la suscripción, configure una **alerta de
presupuesto** (Cost Management → Presupuestos) en US$20 y US$50 para no
quedarse sin crédito por sorpresa.

---

## 1. Crear la máquina virtual

En [portal.azure.com](https://portal.azure.com) → **Máquinas virtuales** → **Crear**:

| Campo | Valor |
|---|---|
| Suscripción | Azure for Students |
| Grupo de recursos | Nuevo: `rg-inventario-sanson` |
| Nombre | `vm-inventario-sanson` |
| Región | La más cercana que permita su suscripción (si una región da error de directiva, pruebe otra: East US 2, Brazil South, South Central US) |
| Imagen | **Ubuntu Server 24.04 LTS** |
| Tamaño | **B1ms** (o B2s) |
| Autenticación | **Clave pública SSH**; usuario `azureuser`; descargue el archivo `.pem` y guárdelo bien |
| Puertos de entrada | SSH (22), HTTP (80), HTTPS (443) |
| Disco | SSD estándar, 30 GB |

Revisar y crear. Al terminar:

1. Entre al recurso → **Configuración de IP** (o la IP pública) → **Configuración**
   → **Etiqueta de nombre DNS**: `inventario-sanson`. Quedará un nombre como
   `inventario-sanson.eastus2.cloudapp.azure.com`. Ese es su **DOMINIO**.
2. **Redes** → regla de entrada del puerto 22 → en *Origen* elija
   **Mi dirección IP**. Así solo usted puede administrar el servidor
   (SEG-IN-02). Los puertos 80 y 443 quedan abiertos a todos: es la página de
   inicio de sesión.

---

## 2. Conectarse

Desde PowerShell (Windows) o una terminal:

```bash
ssh -i ruta/a/vm-inventario-sanson_key.pem azureuser@inventario-sanson.eastus2.cloudapp.azure.com
```

En Windows, si avisa que la clave tiene permisos muy abiertos: clic derecho al
`.pem` → Propiedades → Seguridad → deje solo a su usuario.

---

## 3. Preparar el servidor (una sola vez)

```bash
# Actualizar el sistema
sudo apt update && sudo apt -y upgrade

# 2 GB de memoria de intercambio: evita que la compilación se quede sin memoria
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# Zona horaria del negocio
sudo timedatectl set-timezone America/Lima

# Salir y volver a entrar para que el grupo docker se aplique
exit
```

Vuelva a conectarse con el mismo comando `ssh` del paso 2 y compruebe:

```bash
docker compose version
```

---

## 4. Descargar el sistema

```bash
git clone https://github.com/Egon-10/capstone-andamios-sanson.git
cd capstone-andamios-sanson
# Mientras los pull requests no estén integrados, use la rama del Sprint 4.
# Después de integrarlos, use main.
git checkout feature/sprint-4-seguridad-despliegue
```

---

## 5. Configuración (`.env`)

Reemplace `inventario-sanson.eastus2.cloudapp.azure.com` por su DOMINIO del paso 1:

```bash
DOMINIO=inventario-sanson.eastus2.cloudapp.azure.com
cat > .env <<FIN
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
MYSQL_USER=inventario
MYSQL_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
DOMINIO=${DOMINIO}
PUERTO_WEB=127.0.0.1:8080
CORS_ORIGENES=https://${DOMINIO}
FIN
chmod 600 .env
```

El archivo `.env` contiene las contraseñas de la base y la clave de las
sesiones: **no lo copie a ningún otro lugar ni lo suba a GitHub** (ya está en
`.gitignore`). Si se pierde, la base se puede restaurar desde un respaldo con
una nueva configuración.

---

## 6. Levantar el sistema

```bash
docker compose -f docker-compose.yml -f docker-compose.https.yml up -d --build
```

La primera vez tarda entre 5 y 10 minutos (descarga y compila). Después:

```bash
docker compose -f docker-compose.yml -f docker-compose.https.yml ps
```

`db`, `backend` y `frontend` deben figurar como **healthy** y `https` como
**running**. Abra en el navegador `https://SU-DOMINIO`: debe aparecer la
pantalla de inicio de sesión con el candado de conexión segura.

Para no escribir los dos archivos cada vez:

```bash
echo 'COMPOSE_FILE=docker-compose.yml:docker-compose.https.yml' >> .env
```

Desde entonces basta `docker compose ps`, `docker compose logs`, etc.

---

## 7. Primer acceso del administrador

La base trae las cuentas del volcado de la empresa. Para darle al
administrador una contraseña temporal (que el sistema le obligará a cambiar
al entrar):

```bash
set -a; . ./.env; set +a
mysql_raiz() { docker compose exec -T db mysql -uroot -p"$MYSQL_ROOT_PASSWORD" inventario_andamios "$@"; }

# Ver las cuentas y su rol (1 = administrador, 2 = encargado, 3 = gerente)
mysql_raiz -e "SELECT id, nombre_usuario, correo, rol_id, estado FROM usuarios;"

# Elegir una contraseña temporal (mínimo 10 caracteres, con mayúscula,
# minúscula, número y símbolo) y aplicarla al administrador:
TEMPORAL='Escriba#Aqui2026'
HASH=$(docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" "$TEMPORAL" | tr -d ':\n')
mysql_raiz -e "UPDATE usuarios SET password='$HASH', debe_cambiar_password=1,
               intentos_fallidos=0, bloqueado_hasta=NULL
               WHERE nombre_usuario='NOMBRE_DEL_ADMIN';"
```

Entre con ese usuario y la temporal: el sistema pedirá crear la contraseña
definitiva. Desde la pantalla **Usuarios**, el administrador crea las cuentas
del personal o les genera contraseñas temporales con **Restablecer contraseña**.

---

## 8. Respaldo diario (SEG-BD-04)

```bash
chmod +x respaldo.sh
./respaldo.sh                     # prueba manual
crontab -e                        # y agregar esta línea:
30 23 * * * cd /home/azureuser/capstone-andamios-sanson && ./respaldo.sh >> respaldos/registro.log 2>&1
```

Cada respaldo queda en `respaldos/` y se conservan 30 días. Para tener una
copia fuera del servidor, descárguela de vez en cuando:

```bash
scp -i ruta/a/clave.pem azureuser@SU-DOMINIO:capstone-andamios-sanson/respaldos/*.sql.gz .
```

La restauración está en `docs/despliegue.md`, sección 4.

---

## 9. Actualizar a una nueva versión

```bash
cd ~/capstone-andamios-sanson
./respaldo.sh
git pull
docker compose up -d --build
docker compose ps
```

Si la versión nueva trae una migración (`database/0X-*.sql`), aplíquela antes
de levantar el backend; ver `docs/despliegue.md`, sección 3.

---

## 10. Cuidar el crédito

- Mientras el personal use el sistema (implantación y medición), deje la
  máquina **encendida**.
- Cuando ya no se use (por ejemplo, terminado el ciclo), en el portal elija
  **Detener**: así deja de consumir crédito de cómputo (el disco y la IP siguen
  costando muy poco). Los datos se conservan.
- Si va a eliminar todo, primero descargue un respaldo.

---

## 11. Evidencias para el informe

Guarde capturas de:

1. La máquina virtual en el portal (tamaño, región, estado *En ejecución*).
2. Las reglas de red (22 solo desde su IP; 80 y 443 abiertos).
3. `docker compose ps` con los servicios sanos.
4. El navegador en `https://SU-DOMINIO` con el candado y el certificado.
5. El primer ingreso del administrador (cambio obligatorio de contraseña).
6. El personal usando el sistema en el almacén (con su consentimiento).
7. `ls -lh respaldos/` después de la primera noche.

Con 4 se completa el control **SEG-IN-01** (HTTPS) y con 2 el **SEG-IN-02**.

---

## Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El certificado no se emite (`docker compose logs https`) | Los puertos 80 o 443 no están abiertos, o el DOMINIO no coincide con la etiqueta DNS | Revisar las reglas de red y el `.env` |
| `backend` no llega a *healthy* | Falta memoria | Verificar la memoria de intercambio (`free -h`) o usar B2s |
| "Demasiados intentos" para todos | El proxy no envía la IP real | Ya está resuelto en `nginx.conf` (`real_ip`); confirme que usa la versión actual |
| No puedo entrar por SSH | Su IP cambió | En el portal, actualice la regla del puerto 22 con su IP actual |

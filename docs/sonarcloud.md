# Configuracion de SonarCloud

El analisis estatico del proyecto se apoya en dos herramientas que se
complementan:

| Herramienta | Que revisa | Necesita configuracion |
|---|---|---|
| **CodeQL** | Vulnerabilidades de seguridad en Java y TypeScript | No. Ya funciona |
| **SonarCloud** | Calidad, deuda tecnica, duplicados y cobertura | Si, una sola vez |

CodeQL es nativo de GitHub y se ejecuta en cada *push* y *pull request* sin
configuracion adicional. Sus hallazgos aparecen en la pestana
**Security → Code scanning** del repositorio.

SonarCloud es gratuito para repositorios publicos, pero necesita una cuenta y
un token. Mientras el token no exista, el *workflow* de SonarCloud se omite y
registra un aviso, sin marcar la ejecucion como fallida.

## Pasos para activar SonarCloud

Los pasos 1 a 4 los realiza una sola persona del equipo, una unica vez.

### 1. Crear la cuenta

Entrar a <https://sonarcloud.io> y elegir **Log in with GitHub**. Autorizar el
acceso cuando GitHub lo solicite.

### 2. Crear la organizacion

En SonarCloud, pulsar **+ → Analyze new project**. SonarCloud pide crear
primero una organizacion a partir de la cuenta de GitHub:

- Elegir la cuenta `Egon-10`.
- En el plan, elegir **Free plan** (es el que corresponde a repositorios
  publicos).

La clave de la organizacion debe quedar como `egon-10`. Es el valor que ya
esta escrito en `sonar-project.properties`, en la propiedad
`sonar.organization`.

### 3. Crear el proyecto

Seleccionar el repositorio `capstone-andamios-sanson` y pulsar **Set up**.

En la pantalla siguiente, elegir **With GitHub Actions** como metodo de
analisis. SonarCloud muestra entonces el valor de `SONAR_TOKEN`.

Verificar que la clave del proyecto sea `Egon-10_capstone-andamios-sanson`,
que es el valor de `sonar.projectKey`. Si SonarCloud genera una clave
distinta, hay que corregir `sonar-project.properties` para que coincida.

### 4. Registrar el token en GitHub

Copiar el token que mostro SonarCloud y, en el repositorio de GitHub, ir a:

**Settings → Secrets and variables → Actions → New repository secret**

- **Name:** `SONAR_TOKEN`
- **Secret:** el valor copiado

El token es una credencial. No debe pegarse en el codigo, ni en el informe, ni
en ningun archivo del repositorio: solo en ese formulario.

### 5. Desactivar el analisis automatico

SonarCloud trae activada una opcion llamada **Automatic Analysis** que entra
en conflicto con el analisis lanzado desde GitHub Actions. Hay que apagarla:

**Project → Administration → Analysis Method → Automatic Analysis → Off**

### 6. Comprobar

Hacer cualquier *push* al repositorio y revisar la pestana **Actions**. El job
`SonarCloud` debe ejecutarse y terminar en verde. El resultado queda visible
en el panel de SonarCloud.

## Que se analiza

| Modulo | Rutas |
|---|---|
| Backend | `microservicio/src/main/java` |
| Frontend | `frontend/src` |

Quedan fuera del analisis `node_modules`, los directorios de compilacion
(`target`, `dist`), los archivos de entorno y los archivos de prueba, que se
declaran aparte para que Sonar no los cuente como codigo de produccion.

La cobertura de pruebas se toma de dos reportes que la propia CI genera:

| Modulo | Herramienta | Reporte |
|---|---|---|
| Backend | JaCoCo | `microservicio/target/site/jacoco/jacoco.xml` |
| Frontend | Karma | `frontend/coverage/frontend/lcov.info` |

## Donde consultar los resultados

| Herramienta | Ubicacion |
|---|---|
| CodeQL | Repositorio → **Security** → **Code scanning** |
| SonarCloud | <https://sonarcloud.io/project/overview?id=Egon-10_capstone-andamios-sanson> |
| Cobertura (local) | `microservicio/target/site/jacoco/index.html` |

Para generar el reporte de cobertura del backend en una maquina local:

```bash
cd microservicio
mvn verify
```

Y para el del frontend:

```bash
cd frontend
npx ng test --watch=false --browsers=ChromeHeadless --code-coverage
```

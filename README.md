# Sistema web de control de inventario — Andamios Sansón

Proyecto del curso **Capstone Project** (Ingeniería de Sistemas Computacionales, UPN).
Sistema web para el control de inventario de la empresa Andamios Sansón, dedicada a
la fabricación y comercialización de andamios y estructuras metálicas.

El proyecto se construye sobre un informe y un proyecto previo de control de
inventario para la misma empresa: se reutiliza solo lo pertinente y se desarrolla
de nuevo lo que corresponde a esta etapa del curso, manteniendo separado lo
heredado de lo nuevo.

## Requisitos previos

| Herramienta | Versión | Nota |
|---|---|---|
| Java (JDK) | 17 o superior | `java -version` |
| Node.js | 22 o superior | `node -v` |
| MySQL | 8.0 o superior | También funciona con MariaDB 10.4+ |
| Git | cualquiera | — |

Maven no hace falta instalarlo: el repositorio incluye el wrapper (`mvnw`).

## Puesta en marcha desde cero

### 1. Clonar el repositorio

```bash
git clone https://github.com/Egon-10/capstone-andamios-sanson.git
cd capstone-andamios-sanson
```

### 2. Crear y cargar la base de datos

Los scripts están en `database/` y se ejecutan **en orden**: el `01` trae los datos
de la empresa y el `02` aplica el esquema del Sprint 1.

```bash
# Crear la base vacía
mysql -u root -p -e "CREATE DATABASE inventario_andamios CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# Cargar los datos y aplicar la migración
mysql -u root -p --default-character-set=utf8mb4 inventario_andamios < database/01-inventario_andamios.sql
mysql -u root -p --default-character-set=utf8mb4 inventario_andamios < database/02-sprint1-seguridad.sql
```

En **Windows con PowerShell**, el redirector `<` no existe, así que hay que usar `cmd /c`
y la ruta completa del cliente (ajústela a su instalación):

```powershell
$mysql = "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe"

& $mysql -u root -p -e "CREATE DATABASE inventario_andamios CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
cmd /c "`"$mysql`" -u root -p --default-character-set=utf8mb4 inventario_andamios < database\01-inventario_andamios.sql"
cmd /c "`"$mysql`" -u root -p --default-character-set=utf8mb4 inventario_andamios < database\02-sprint1-seguridad.sql"
```

También puede importarlos desde MySQL Workbench o phpMyAdmin, siempre en ese orden.
El detalle completo está en [`database/README.md`](database/README.md).

### 3. Configurar el backend

```bash
cd microservicio
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Abra ese archivo y complete:

- `spring.datasource.username` y `spring.datasource.password`: sus credenciales de MySQL.
- `app.jwt.secret`: cualquier frase de **32 caracteres o más**. Firma los tokens de
  sesión. Si la deja vacía, el servidor genera una clave temporal y las sesiones se
  cierran cada vez que reinicia.

El archivo `application.properties` está en `.gitignore`: sus credenciales no se suben
al repositorio.

### 4. Levantar el backend

```bash
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Queda escuchando en `http://localhost:8081`.

### 5. Levantar el frontend

En otra terminal:

```bash
cd frontend
npm install
npm start
```

Abra `http://localhost:4200`.

## Cuentas de prueba

| Correo | Usuario | Contraseña | Rol |
|---|---|---|---|
| `admin@gmail.com` | `admin` | `Andamios2026` | ADMINISTRADOR |
| `encargado@gmail.com` | `encargado` | `Encargado2026` | ENCARGADO |
| `gerente@gmail.com` | `gerente` | `Gerente2026` | GERENTE |

El inicio de sesión acepta el correo o el nombre de usuario. Son credenciales **solo
para desarrollo**: antes de usar el sistema en la empresa hay que cambiarlas.

## Estructura del repositorio

```
.
├── database/        # Volcado de la empresa y migración del esquema
├── frontend/        # Aplicación web (Angular 20)
├── microservicio/   # API REST (Spring Boot 4.1 / Java 17)
└── .github/
    └── workflows/   # Integración continua
```

## Stack tecnológico

- **Frontend:** Angular 20 con TypeScript
- **Backend:** Spring Boot 4.1, Java 17, Spring Data JPA, Spring Security
- **Base de datos:** MySQL 8
- **Pruebas:** JUnit 5 y Mockito (backend); Jasmine y Karma (frontend)

## Seguridad y roles

| Rol | Puede |
|---|---|
| ADMINISTRADOR | Todo, incluida la gestión de usuarios, roles y categorías |
| GERENTE | Consultar, registrar y editar productos, proveedores y movimientos; ver auditoría |
| ENCARGADO | Consultar y registrar movimientos |

- Las contraseñas se almacenan con BCrypt; nunca en texto plano.
- El inicio de sesión devuelve un token de acceso (15 minutos) y uno de renovación
  con rotación: cada token de renovación vale una sola vez.
- La sesión se cierra tras 30 minutos sin actividad, con aviso un minuto antes.
- Al cerrar sesión, el token de acceso queda invalidado en el servidor.
- **Las reglas de acceso se aplican en el servidor** (`SecurityConfig` y `@PreAuthorize`).
  El cliente solo oculta lo que el usuario no puede usar; esconder un botón no es una
  medida de seguridad.

## Pruebas

```bash
# Backend: 38 pruebas unitarias
cd microservicio
./mvnw test -Dtest='*ServiceTest,*ValidatorTest,*HandlerTest,*InitializerTest'

# Frontend: 9 pruebas
cd frontend
npm test -- --watch=false
```

`MicroservicioApplicationTests` queda fuera de ese filtro porque levanta el contexto
completo de Spring y necesita MySQL en ejecución.

Cada push ejecuta en GitHub Actions las pruebas del backend, las del frontend y la
carga de la base contra un MySQL 8 real.

## Equipo

| Integrante | Rol |
|---|---|
| Yeferson Lopez Diaz | Product Owner |
| Pablo Eleazar Estupiñan Elera | Scrum Master |
| Angel Gabriel Poma Rosales | Líder técnico |
| Aracely Quispe Rosales | Líder de calidad |
| Marlon Mario Piscoya Jayme | Responsable de seguridad |
| Jerson Burga Espinoza | Responsable de campo y datos |
| Josue Benjamin Villaran Canales | Analista de pruebas |
| Axel Emanuel Tapa Mondragon | Analista de datos y documentación |

## Flujo de trabajo

| Rama | Propósito |
|---|---|
| `main` | Versión estable y desplegable |
| `develop` | Integra el trabajo terminado de cada sprint |
| `feature/<hu>-<descripcion>` | Desarrollo de una historia de usuario |
| `fix/<id>-<descripcion>` | Corrección de un defecto |

Ningún cambio se integra directamente en `main` ni en `develop`: toda incorporación
se hace mediante pull request con revisión de al menos un integrante distinto del autor.

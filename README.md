# Capstone Project — Sistema de Control de Inventario (Andamios Sansón)

Proyecto del curso **Capstone Project** (Ingeniería de Sistemas Computacionales, UPN). Se construye sobre un informe y proyecto previo de control de inventario para la empresa **Andamios Sansón**, reutilizando lo pertinente y desarrollando de nuevo lo que corresponde a esta etapa del curso.

## Estructura del repositorio

```
.
├── frontend/       # Aplicación web (Angular 20)
└── microservicio/  # API backend (Spring Boot 4.1 / Java 17)
```

## Stack tecnológico

- **Frontend:** Angular 20
- **Backend:** Spring Boot 4.1, Java 17, Spring Data JPA / Hibernate
- **Base de datos:** MySQL

## Equipo

| Integrante | Rol |
|---|---|
| Pablo Eleazar Estupiñan Elera | Scrum Master |
| Yeferson Lopez Diaz | Product Owner |
| Aracely Quispe Rosales | Equipo |
| Angel Gabriel Poma Rosales | Equipo |
| Jerson Burga Espinoza | Equipo |
| Josue Benjamin Villaran Canales | Equipo |
| Marlon Mario Piscoya Jayme | Equipo |
| Axel Emanuel Tapa Mondragon | Equipo |

## Cómo levantar el proyecto en local

### 1. Backend (microservicio)

1. Entra a la carpeta `microservicio/`.
2. Copia `src/main/resources/application.properties.example` a `src/main/resources/application.properties`.
3. Completa tu usuario y contraseña de MySQL local en ese archivo (este archivo está en `.gitignore`, así que tus credenciales no se suben al repo).
4. Crea la base de datos `inventario_andamios` en tu MySQL local (o ajusta el nombre en `application.properties`).
5. **Seguridad (Sprint 1).** Agrega a tu `application.properties` las propiedades `app.*` del archivo de ejemplo:
   - `app.jwt.secret`: un secreto de al menos 32 caracteres para firmar los tokens. Si no lo defines, el servidor genera uno temporal y las sesiones se cierran al reiniciar.
   - `app.admin.correo` y `app.admin.password`: solo se usan para crear el administrador inicial cuando la tabla de usuarios está vacía.
   - Al primer arranque, las contraseñas que estaban en texto plano se cifran con BCrypt automáticamente; cada usuario sigue ingresando con su misma contraseña.
6. Ejecuta las pruebas unitarias:
   ```bash
   ./mvnw test -Dtest='*ServiceTest,*ValidatorTest,*HandlerTest,*InitializerTest'
   ```
   (La prueba `MicroservicioApplicationTests` levanta el contexto completo y necesita MySQL en ejecución.)
7. Ejecuta:
   ```bash
   ./mvnw spring-boot:run
   ```
   El backend queda disponible en `http://localhost:8081`.

### 2. Frontend

1. Entra a la carpeta `frontend/`.
2. Instala dependencias:
   ```bash
   npm install
   ```
3. Levanta el servidor de desarrollo:
   ```bash
   npm start
   ```
4. Abre `http://localhost:4200` en el navegador.
5. Pruebas unitarias del cliente:
   ```bash
   npm test -- --watch=false
   ```

## Seguridad y roles (Sprint 1)

| Rol | Puede |
|---|---|
| ADMINISTRADOR | Todo, incluida la gestión de usuarios, roles y categorías |
| GERENTE | Consultar, registrar y editar productos, proveedores y movimientos; ver auditoría |
| ENCARGADO | Consultar y registrar movimientos |

- El inicio de sesión acepta correo o nombre de usuario y devuelve un token de acceso (15 min) y uno de renovación.
- La sesión se cierra tras 30 minutos sin actividad; el cliente avisa un minuto antes.
- Las reglas de acceso se aplican en el servidor (`SecurityConfig`); el cliente solo oculta lo que el usuario no puede usar.

## Notas

- Cada integrante del equipo debe configurar su propio `application.properties` local (no se comparte por git).
- El proyecto no es una copia del sistema anterior: se reutiliza solo lo pertinente, manteniendo separado lo heredado de lo que se genera nuevo para esta etapa.

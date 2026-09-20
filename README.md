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
5. Ejecuta:
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

## Notas

- Cada integrante del equipo debe configurar su propio `application.properties` local (no se comparte por git).
- El proyecto no es una copia del sistema anterior: se reutiliza solo lo pertinente, manteniendo separado lo heredado de lo que se genera nuevo para esta etapa.

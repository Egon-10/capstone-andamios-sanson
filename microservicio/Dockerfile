# =====================================================================
# Imagen del backend (HU-42: despliegue reproducible)
#
# Se compila dentro de la imagen a partir del código fuente, de modo que
# el resultado no depende de lo que haya instalado en la máquina de quien
# despliega: misma versión de Java, mismas dependencias, mismo artefacto.
# =====================================================================

# --- Etapa 1: compilación -------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS construccion
WORKDIR /fuente

# Primero solo el pom: si el código cambia pero las dependencias no, Docker
# reutiliza esta capa y la reconstrucción tarda segundos en lugar de minutos.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests \
 && cp target/microservicio-*.jar /fuente/app.jar

# --- Etapa 2: ejecución ---------------------------------------------------
# Solo el JRE, sin Maven ni el código fuente: imagen más pequeña y con menos
# superficie de ataque.
FROM eclipse-temurin:17-jre-alpine

# El proceso no corre como root (SEG-INF-02).
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=construccion /fuente/app.jar app.jar
# La configuración no lleva secretos: todos los valores sensibles llegan por
# variables de entorno desde docker-compose (.env).
COPY src/main/resources/application.properties.example config/application.properties

USER app
EXPOSE 8081

# El orquestador considera sano el contenedor cuando la aplicación responde.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8081/actuator/health | grep -q '"UP"' || exit 1

ENV TZ=America/Lima
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

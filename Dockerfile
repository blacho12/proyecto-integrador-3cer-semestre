# ---------------------------------------------------------------------------
# SGMI - API REST Spring Boot
# Imagen multi-etapa: compila con Maven/JDK 21 y ejecuta con un JRE 21 liviano.
# Pensada para el servicio "Web Service" de Render (runtime Docker).
# ---------------------------------------------------------------------------

# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Se copia primero el POM para aprovechar la cache de dependencias de Docker.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Etapa 2: ejecucion ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# La aplicacion no necesita privilegios de root.
RUN addgroup -S sgmi && adduser -S sgmi -G sgmi

COPY --from=build /app/target/gestor-mantenimiento-1.0.0.jar app.jar

USER sgmi

# Render inyecta el puerto real en la variable PORT; application.properties
# usa ${PORT:8080}. EXPOSE es solo informativo.
EXPOSE 8080

# MaxRAMPercentage ajusta el heap al limite de memoria del contenedor.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

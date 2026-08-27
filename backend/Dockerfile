# --- build stage: compila el jar con Maven y Java 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# cache de dependencias: primero el pom, luego el codigo fuente
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
# el jar se empaqueta sin correr los tests (los tests corren en `mvn test` / CI)
RUN mvn -B -q clean package -DskipTests

# --- runtime stage: solo JRE 21 + el jar ---
FROM eclipse-temurin:21-jre-jammy AS runtime
# curl para el healthcheck del compose
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# usuario no root
RUN useradd --system --uid 10001 riwi
COPY --from=build /build/target/*.jar app.jar
USER riwi
EXPOSE 8080
# el puerto real y la config llegan por variables de entorno (ver docker-compose.yml)
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

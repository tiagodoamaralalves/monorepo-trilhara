# Etapa 1: Build da aplicação (Backend Quarkus + Empacotamento do Frontend)
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

# Copia o pom.xml primeiro para aproveitar cache de camadas das dependências
COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -f backend/pom.xml dependency:go-offline -B

# Copia o código-fonte do backend
COPY backend/src backend/src

# Compila e empacota a aplicação Quarkus
RUN --mount=type=cache,target=/root/.m2 mvn -f backend/pom.xml clean package -Dmaven.test.skip=true -B

# Etapa 2: Imagem de execução (Runtime leve com JRE 25)
FROM eclipse-temurin:25-jre

WORKDIR /app

# Executa como usuario sem privilegios
RUN useradd --system --no-create-home --shell /usr/sbin/nologin quarkus

COPY --chown=quarkus --from=build /workspace/backend/target/quarkus-app/lib/ /app/lib/
COPY --chown=quarkus --from=build /workspace/backend/target/quarkus-app/*.jar /app/
COPY --chown=quarkus --from=build /workspace/backend/target/quarkus-app/app/ /app/app/
COPY --chown=quarkus --from=build /workspace/backend/target/quarkus-app/quarkus/ /app/quarkus/

USER quarkus

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", "-jar", "/app/quarkus-run.jar"]

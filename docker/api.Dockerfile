# Contexte de build : la racine du dépôt (voir docker-compose.yml)

FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre
# ffmpeg : extraction des miniatures vidéo (phase 1). curl : healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/travel-tracker.jar /app/app.jar
# Le fichier de configuration externe est monté dans /app/config/application.yml
ENV JAVA_OPTS="-Xms256m -Xmx1g -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

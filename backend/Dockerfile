# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# --- run ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# ffmpeg      : vignette + durée des vidéos (VideoThumbnailService)
# libheif-examples : heif-convert, pour les photos HEIC des iPhone
RUN apt-get update && apt-get install -y --no-install-recommends \
      ffmpeg libheif-examples \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/travel-tracker.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

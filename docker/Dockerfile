# Image unique : nginx (frontend + proxy /api + médias) et Spring Boot dans le même conteneur.
# Contexte de build : la racine du dépôt (voir docker-compose.yml)

# --- Étape 1 : frontend Vue.js ---
FROM node:24-alpine AS frontend-builder
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# --- Étape 2 : backend Spring Boot ---
FROM maven:3-eclipse-temurin-25 AS backend-builder
WORKDIR /backend
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src src
RUN mvn -B -q package -DskipTests

# --- Étape 3 : image d'exécution ---
FROM eclipse-temurin:25-jre-jammy AS runtime
# nginx ; ffmpeg (miniatures vidéo) ; curl (healthcheck) ; tzdata ; libheif-examples (HEIC)
RUN apt-get update && apt-get install -y --no-install-recommends \
        nginx ffmpeg curl tzdata libheif-examples \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /home/app app

# Le fuseau du conteneur est l'UTC : le backend et les journaux en dépendent (voir README).
ENV TZ=UTC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"

WORKDIR /app
COPY --from=backend-builder /backend/target/travel-tracker.jar /app/app.jar

# Frontend servi par nginx
RUN rm -rf /usr/share/nginx/html/*
COPY --from=frontend-builder /frontend/dist /usr/share/nginx/html

# nginx : configuration complète (le conteneur tourne sans root : fichiers temporaires dans /tmp)
COPY docker/nginx.conf /etc/nginx/nginx.conf

COPY docker/rotate-logs.sh /usr/local/bin/rotate-logs.sh
COPY docker/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /usr/local/bin/rotate-logs.sh /app/docker-entrypoint.sh \
    && chmod +x /usr/local/bin/rotate-logs.sh /app/docker-entrypoint.sh

# nginx tente d'ouvrir son journal d'erreurs par défaut avant de lire la configuration : dossier accessible à tout UID
RUN mkdir -p /var/log/nginx && chmod 777 /var/log/nginx

# Médias et journaux : dossiers montés depuis le NAS
VOLUME ["/data"]

USER app
EXPOSE 18081

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl -fs -o /dev/null http://127.0.0.1:18081/healthz && curl -fs -o /dev/null http://127.0.0.1:18080/api/actuator/health || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]

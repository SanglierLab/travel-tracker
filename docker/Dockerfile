# --- Étape 1 : Build du frontend Vue.js ---
FROM node:22-alpine AS frontend-builder
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# --- Étape 2 : Build du backend Spring Boot ---
FROM maven:3.9-eclipse-temurin-25 AS backend-builder
WORKDIR /backend
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn clean package -DskipTests -B \
    && echo "=== CONTENU DE /backend/target ===" \
    && ls -lah /backend/target

# --- Étape 3 : Image de runtime complète ---
FROM eclipse-temurin:25-jre-jammy AS runtime
# Installer nginx, ffmpeg, tzdata
RUN apt-get update && apt-get install -y --no-install-recommends \
    nginx \
    ffmpeg \
    tzdata \
    libheif-examples \
  && rm -rf /var/lib/apt/lists/*

# Créer user/group non-root
RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /home/app  app

ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Europe/Paris

WORKDIR /app
COPY --from=backend-builder /backend/target/travel-tracker.jar /app/app.jar
RUN chown app:app /app/app.jar && chmod 644 /app/app.jar

# Copier les fichiers statiques Vue.js dans nginx
RUN rm -rf /usr/share/nginx/html/*
COPY --from=frontend-builder /frontend/dist /usr/share/nginx/html

# Ajouter la conf nginx
COPY docker/nginx.conf /etc/nginx/nginx.conf

# Ajouter script d'entrée
COPY docker/docker-entrypoint.sh /app/docker-entrypoint.sh

RUN sed -i 's/\r$//' /app/docker-entrypoint.sh \
    && chmod +x /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh

# Volume pour les médias montés sur NAS
VOLUME ["/data"]

# Permissions
RUN chown -R app:app /app /usr/share/nginx/html /var/lib/nginx

RUN mkdir -p \
        /var/log/nginx \
        /var/cache/nginx \
        /run/nginx \
    && touch \
        /var/log/nginx/access.log \
        /var/log/nginx/error.log \
    && chown -R app:app \
        /var/log/nginx \
        /var/cache/nginx \
        /run/nginx

USER app
EXPOSE 18081 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
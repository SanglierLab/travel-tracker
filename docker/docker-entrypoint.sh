#!/bin/sh
# Démarrer nginx en mode non-daemon
nginx

# Lancer l'application Spring Boot
exec java -XX:MaxRAMPercentage=75 -jar /app/app.jar
#!/bin/sh
# Dossiers de travail (le conteneur peut tourner sous l'UID du NAS : tout est créé à l'exécution).
LOG_DIR="${LOG_DIR:-/data/logs}"
mkdir -p "$LOG_DIR/api" "$LOG_DIR/web" /tmp/nginx

# Rotation des journaux d'accès de nginx : au démarrage puis toutes les 15 minutes
# (compresse les jours passés, supprime les archives de plus de 30 jours ; le script ne touche jamais au fichier du jour).
( while true; do /usr/local/bin/rotate-logs.sh "$LOG_DIR/web" 30; sleep 900; done ) &

# nginx passe en arrière-plan ; ses erreurs vont sur la console de Docker (error_log /dev/stderr).
nginx

# Spring Boot : le journal du backend est dans $LOG_DIR/api (voir application.yml).
export LOG_DIR="$LOG_DIR/api"
exec java $JAVA_OPTS -jar /app/app.jar

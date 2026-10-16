#!/bin/sh
# Exécuté par l'entrée standard de l'image nginx (/docker-entrypoint.d/) avant le démarrage de nginx :
# prépare le dossier des journaux et lance cron, qui compresse les journaux de la veille chaque nuit.
set -e
LOG_DIR=/var/log/travel-tracker
mkdir -p "$LOG_DIR"
chown nginx:nginx "$LOG_DIR" 2>/dev/null || true   # les processus nginx tournent sous l'utilisateur « nginx »
chmod 755 "$LOG_DIR"

mkdir -p /etc/crontabs
echo '5 0 * * * /usr/local/bin/rotate-logs.sh' > /etc/crontabs/root
/usr/local/bin/rotate-logs.sh || true              # au démarrage : rattrape une nuit où le conteneur était arrêté
crond -b -L /dev/null

#!/bin/sh
# Rotation des journaux d'accès de nginx : les fichiers des jours passés (access-AAAA-MM-JJ.log) sont compressés en .gz,
# les archives de plus de 30 jours sont supprimées. Le fichier du jour (heure UTC, comme nginx dans son conteneur) n'est pas touché.
# nginx écrit directement dans un fichier par jour : il n'y a donc rien à rouvrir ni à signaler.
# Usage : rotate-logs.sh [dossier] [jours]
DIR="${1:-/data/logs/web}"
KEEP_DAYS="${2:-30}"
TODAY="$(date -u +%Y-%m-%d)"

[ -d "$DIR" ] || exit 0

for f in "$DIR"/access-*.log; do
    [ -f "$f" ] || continue
    case "$f" in
        *"/access-$TODAY.log") continue ;;
    esac
    gzip -f "$f"
done

find "$DIR" -maxdepth 1 -type f -name 'access-*.log.gz' -mtime +"$KEEP_DAYS" -exec rm -f {} +

# travel-tracker 2.0.0

Site de partage de voyage (galeries photos/vidéos, position, trajets). Spécification complète : [SPECIFICATIONS.md](SPECIFICATIONS.md).

**État : phase 1 (galeries et médias, côté API).** Tout se teste avec `curl` (voir plus bas) ; les écrans arrivent aux phases 2 et 3.

## Arborescence

```
backend/    Spring Boot 4.1.1, Java 25, Maven (fr.sanglierlab:travel-tracker:2.0.0)
frontend/   Vue 3.5, Vite 8, Vue Router 5
docker/     docker-compose.yml, Dockerfiles, nginx.conf, exemples de configuration, SQL d'initialisation
```

## Mise en production sur le NAS

### 1. Dossiers

```
/volume1/docker/travel-tracker/
├── config/application.yml     (créé à l'étape 3)
└── media/                     (dossier des médias, propriétaire = PUID:PGID de .env)
```

### 2. Base de données

Exécuter `docker/sql/init-db.sql` avec un compte administrateur MariaDB (après avoir remplacé `CHANGE_ME`).
Les tables sont créées automatiquement par Flyway au premier démarrage de l'API.

### 3. Configuration

Copier `docker/config/application.example.yml` vers `/volume1/docker/travel-tracker/config/application.yml` et renseigner :
mot de passe de la base, comptes admin, token d'API.

**Mot de passe admin** (génère un hash bcrypt sans rien installer) :

```
docker run --rm httpd:alpine htpasswd -nbBC 10 "" 'MonMotDePasse' | tr -d ':\n'
```

Coller le résultat (`$2y$10$...`) dans `password-hash`, entre apostrophes.

**Token d'API** : `openssl rand -hex 32`

### 4. Lancement

```
cd docker
cp .env.example .env        # puis adapter TT_BASE_DIR, PUID, PGID
docker compose --env-file .env up -d --build
docker compose logs -f api  # doit finir par « Started TravelTrackerApplication »
```

Le premier build est long sur un DS220+ (téléchargement des dépendances Maven et npm).

### 4 bis. Variante : construire sur le PC, installer sur le NAS

```
# Sur le PC (dossier docker/)
docker compose build
docker save travel-tracker-api:2.0.0 travel-tracker-web:2.0.0 | gzip > travel-tracker-images.tar.gz

# Sur le NAS, après avoir copié l'archive
gunzip -c travel-tracker-images.tar.gz | docker load
docker compose --env-file .env up -d        # sans --build : les images sont déjà là
```

Si le PC est un Mac Apple Silicon, construire pour le NAS (Intel) avec `DOCKER_DEFAULT_PLATFORM=linux/amd64 docker compose build`.
Sur le NAS, seuls `docker-compose.yml`, `.env` et les dossiers `config/` et `media/` sont nécessaires.

### 5. HAProxy

Pointer le sous-domaine vers `127.0.0.1:18081` (conteneur `web`). Éléments indispensables :

```
backend travel_tracker
    option forwardfor
    http-request set-header X-Forwarded-Proto https
    timeout server 15m          # uploads de vidéos
    server web 127.0.0.1:18081
```

Penser aussi à un `timeout client` suffisant sur le frontend HTTPS concerné.

### 6. Vérifications

- `https://<sous-domaine>/` affiche « Journal de voyage ».
- `https://<sous-domaine>/robots.txt` renvoie `Disallow: /`.
- `curl -I https://<sous-domaine>/` montre l'en-tête `X-Robots-Tag: noindex, nofollow, noarchive`.
- `/connexion` permet de se connecter, puis `/admin` affiche le menu.
- Après 5 mots de passe erronés, la connexion répond « Trop de tentatives ».


## Tester l'API (phase 1)

Les routes d'écriture exigent une session ET le jeton CSRF. Exemple avec `curl` (en développement : `BASE=http://localhost:8080`,
avec `spring.profiles.active: dev` et `secure: false` dans `backend/config/application.yml`) :

```bash
BASE=http://localhost:8080
JAR=/tmp/tt.cookies

# 1. Cookie XSRF-TOKEN, puis connexion
curl -s -c $JAR $BASE/api/auth/me
XSRF=$(awk '$6=="XSRF-TOKEN" {print $7}' $JAR)
curl -s -b $JAR -c $JAR -H "X-XSRF-TOKEN: $XSRF" -d username=voyageur -d password='MonMotDePasse' $BASE/api/auth/login

# 2. Créer une galerie
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -d '{"title":"Palais impérial","placeName":"Tokyo","galleryDate":"2026-10-10","latitude":35.6852,"longitude":139.7528}' \
  $BASE/api/admin/galleries

# 3. Ajouter une photo, une vidéo (un fichier par appel), puis un texte
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -F file=@photo.jpg $BASE/api/admin/galleries/1/items/media
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -F file=@video.mp4 $BASE/api/admin/galleries/1/items/media
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -d '{"markdown":"Une **belle** matinée au palais."}' $BASE/api/admin/galleries/1/items/text

# 4. Monter l'élément 3 d'une position, le supprimer
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X POST "$BASE/api/admin/items/3/move?direction=UP"
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X DELETE $BASE/api/admin/items/3

# 5. Lecture publique (sans cookie)
curl -s $BASE/api/public/galleries?page=1
curl -s $BASE/api/public/map
```

Les fichiers sont écrits dans `<media-dir>/<id galerie>/` : `<uuid>-thumb.jpg`, `<uuid>-display.jpg`, `<uuid>-original.<ext>`.
Supprimer un élément ou une galerie efface aussi les fichiers. Un fichier non reconnu (même renommé en `.jpg`) est refusé avec un message en français.

## Développement local

1. Créer `backend/config/application.yml` (dossier ignoré par git) à partir de l'exemple, avec `server.servlet.session.cookie.secure: false`.
2. Backend : `cd backend && mvn spring-boot:run` (port 8080).
3. Frontend : `cd frontend && npm install && npm run dev` (le proxy Vite relaie `/api` vers 8080).
4. Tests : `cd backend && mvn test`.
5. ffmpeg doit être accessible (dans le PATH, ou via `app.ffmpeg-path`) pour les miniatures de vidéos et les images WebP.

## Notes

- **Réseau Docker** : les deux conteneurs sont en `network_mode: host` pour joindre MariaDB sur `127.0.0.1:3306`. L'API n'écoute que sur `127.0.0.1:18080`.
- **Sessions** : conservées en mémoire. Un redémarrage de l'API déconnecte les admins (sans gravité).
- **Thème** : `frontend/public/theme.css` est le fichier de variables ; voir le commentaire dans `docker-compose.yml` pour le remplacer sans reconstruire l'image.

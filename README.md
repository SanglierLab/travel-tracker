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

## Configurer GPSLogger (position en temps réel)

[GPSLogger](https://github.com/mendhak/gpslogger) (Android, gratuit) envoie chaque position au serveur au fur et à mesure.
Version vérifiée : v136 (juillet 2026). À télécharger sur GitHub (page « Releases ») ou sur F-Droid.
Dans l'application : *Logging details* → activer **Log to custom URL**, puis ouvrir ses réglages
(les libellés exacts peuvent varier légèrement d'une version à l'autre) :

| Champ | Valeur |
|---|---|
| **URL** | `https://voyage.sanglierlab.fr/api/track/points` (remplacer par ton vrai sous-domaine) |
| **HTTP Body** | `{"latitude":%LAT,"longitude":%LON,"time":"%TIME"}` |
| **HTTP Headers** | `Content-Type: application/json` puis, à la ligne : `X-API-Token: <ton token>` |
| **HTTP Method** | `POST` |
| **Basic Authentication** | laisser vide |

Le token est celui de `app.api-token` dans `application.yml`. Il passe dans un en-tête, jamais dans l'URL.

**Si l'heure est refusée** (message « Date illisible » dans les journaux de GPSLogger), remplacer le corps par
`{"latitude":%LAT,"longitude":%LON,"timestamp":%TIMESTAMP}` : l'API accepte aussi un horodatage en secondes.

**Réglages conseillés** (à ajuster à l'usage) : autoriser la localisation « tout le temps » pour l'application, la dispenser de
l'optimisation de batterie, et choisir un intervalle de 60 à 300 secondes avec un filtre de distance (50 m) pour économiser batterie et données.

### Tester sans téléphone

```bash
BASE=https://voyage.sanglierlab.fr    # ou http://localhost:8080 en développement
TOKEN='<ton token>'

# Premier envoi : {"status":"created"}
curl -i -X POST $BASE/api/track/points -H "Content-Type: application/json" -H "X-API-Token: $TOKEN" \
  -d '{"latitude":48.8583,"longitude":2.2945,"time":"2026-10-04T10:00:00.000Z"}'
# Même envoi : {"status":"duplicate"} (code 200 aussi : le point existe déjà, rien n'est ajouté)
# Mauvais token : 401   |   sans « Content-Type: application/json » : 415   |   latitude > 90 : 400
```

Vérifier en base : `SELECT * FROM track_point ORDER BY id DESC LIMIT 5;` (heures en UTC).

**Au premier essai réel**, vérifier que les coordonnées enregistrées sont correctes (séparateur décimal : un point, pas une virgule)
et que l'heure du point est bien celle de la prise de position et non celle de l'envoi.
Hors connexion, GPSLogger peut renvoyer des points en rafale plus tard : l'API utilise l'heure du point et ignore les doublons.

### Consulter le tracé

```bash
curl -s $BASE/api/public/track | head -c 600
```

La réponse contient les lignes à dessiner (`segments`, points `[latitude, longitude]` déjà allégés, coupés quand le téléphone est resté plus de
`app.track-gap-hours` sans envoyer de position) et la dernière position connue (`last`). L'affichage sur la carte arrive à l'étape 4b-2.

## Personnaliser le thème

La mise en page ne change jamais ; seules les couleurs, polices et formes se règlent, dans un fichier de variables CSS.

1. Copier `frontend/public/theme.css` (thème par défaut) ou `frontend/themes/japon.css` (exemple) vers `<TT_BASE_DIR>/theme/theme.css` sur le NAS.
2. Dans `docker/docker-compose.yml`, décommenter la ligne `theme.css` du service `web`.
3. `docker compose up -d` : le nouveau thème est pris en compte au rechargement de la page, sans reconstruire d'image.

Le fichier doit être complet (toutes les variables du thème par défaut) et exister avant le démarrage du conteneur.

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

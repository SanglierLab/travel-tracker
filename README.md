# travel-tracker 2.0.0

Site de partage de voyage (galeries photos/vidéos, position, trajets). Spécification complète : [SPECIFICATIONS.md](SPECIFICATIONS.md).

**État : phase 0 (socle).** Le site démarre, la connexion admin fonctionne, l'écran public est vide.

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

## Développement local

1. Créer `backend/config/application.yml` (dossier ignoré par git) à partir de l'exemple, avec `server.servlet.session.cookie.secure: false`.
2. Backend : `cd backend && mvn spring-boot:run` (port 8080).
3. Frontend : `cd frontend && npm install && npm run dev` (le proxy Vite relaie `/api` vers 8080).
4. Tests : `cd backend && mvn test`.

## Notes

- **Réseau Docker** : les deux conteneurs sont en `network_mode: host` pour joindre MariaDB sur `127.0.0.1:3306`. L'API n'écoute que sur `127.0.0.1:18080`.
- **Sessions** : conservées en mémoire. Un redémarrage de l'API déconnecte les admins (sans gravité).
- **Thème** : `frontend/public/theme.css` est le fichier de variables ; voir le commentaire dans `docker-compose.yml` pour le remplacer sans reconstruire l'image.

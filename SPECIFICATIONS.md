# travel-tracker 2.0.0 — Spécifications

> Site de partage de voyage (photos, vidéos, position, trajets), alternative « maison » à Polarsteps.
> Usage familial. Consultation libre, administration réservée à quelques voyageurs.
>
> Projet : `fr.sanglierlab:travel-tracker:2.0.0` (réécriture complète, la version 1 existe déjà).
> Statut du document : spécification validée, avant toute ligne de code.

---

## 1. Principes directeurs

- **Simplicité avant tout** : pas d'usine à gaz, pas de paramétrage à outrance.
- **Léger** : peu de dépendances, peu d'animations (fluide sur un mobile ancien).
- **Autonome** : aucun service externe, à l'exception des tuiles OpenStreetMap et, plus tard, des fournisseurs de données ADS-B et AIS.
- **Mobile d'abord pour l'admin** (utilisée en voyage depuis un téléphone), **confortable aussi sur grand écran** pour le public.
- **Français uniquement** pour l'interface, mais saisie libre de tout caractère (japonais, etc.) sans traduction.

---

## 2. Besoins utilisateurs

### 2.1 Acteurs

| Acteur | Droits |
|---|---|
| Visiteur | Consultation de la partie publique uniquement. Aucun compte, aucune inscription. |
| Admin (voyageur) | Toute modification. Comptes définis dans la configuration du serveur. |
| Outil mobile (GPSLogger) | Envoi de positions via l'API, authentifié par un token. |

### 2.2 Partie publique

- Une **carte** avec un pictogramme par galerie et le **tracé du parcours**, dont le style dépend de la source (`device`, `adsb`, `ais`).
- Une **liste de galeries** en ordre anti-chronologique, **paginée** (pas de défilement infini, pas de recherche), reliées par une **timeline verticale** qui donne une impression de suite logique.
- **Synchronisation carte / liste** :
  - un clic sur un marqueur affiche la galerie correspondante, avec changement de page si nécessaire ;
  - un clic sur une galerie la met en évidence sur la carte.
- La carte affiche **toutes** les galeries et tout le tracé, quelle que soit la page courante.
- Chaque galerie a une **adresse directe** (`/galerie/12`) qui ouvre la bonne page et centre la carte.
- **Photos** : affichées en miniatures ; un clic les ouvre en plein écran ; un bouton permet de télécharger l'originale.
- **Vidéos** : miniature extraite de la vidéo avec un pictogramme de lecture par-dessus ; un clic ouvre la lecture en plein écran.
- **Blocs de texte** intercalés entre les médias (markdown minimal).
- Le bouton « retour » du téléphone ferme la visionneuse au lieu de quitter le site.

### 2.3 Partie admin

- **Menu principal** avec de grosses icônes (Galeries, Trajets, Points, Config, Voir le site, Déconnexion), et une barre de navigation en bas sur mobile.
- **Galeries** : créer, modifier, supprimer (avec avertissement de confirmation).
- **Création d'une galerie** : un morceau de carte permet de placer directement un point. Un bouton « Me positionner » place automatiquement le point grâce à la géolocalisation du navigateur. Dans les deux cas, le point **reste déplaçable**.
- **Contenu d'une galerie** : ajout progressif de photos, vidéos et blocs de texte ; suppression d'éléments ; réordonnancement par boutons monter/descendre.
- **Upload** : sélection de plusieurs fichiers, envoi **un par un** avec progression par fichier et bouton « Réessayer » en cas d'échec. L'admin choisit ce qu'il envoie selon sa connexion (4G, wifi d'hôtel…).
- **Points de trajet** : page dédiée pour lister, modifier ou supprimer un point (point mal placé, par exemple).
- **Trajets suivis** (vols et traversées) : création et configuration (voir §3.4).
- **Config** : page en lecture seule (informations de version, limites, nombre de points…). Les secrets ne s'affichent jamais.

### 2.4 API de positions en temps réel

- Un outil de géolocalisation mobile (**GPSLogger** sous Android) envoie les positions au serveur.
- Chaque point est stocké pas à pas en base (latitude, longitude, heure).
- Plusieurs **sources** sont possibles, représentées différemment sur la carte : `device` (téléphone), `adsb` (transport aérien), `ais` (transport maritime).

### 2.5 Hors périmètre (pour l'instant)

- Likes et commentaires (phase 2 éventuelle).
- Transcodage des vidéos (pas d'iPhone dans la famille : les fichiers sont servis tels quels).
- Code fonctionnel du batch ADS-B / AIS (seuls le stockage et la configuration sont prévus maintenant).
- Recherche, inscription, multilingue, regroupement de marqueurs (clustering).

---

## 3. Règles de gestion

### 3.1 Galeries

1. Champs obligatoires : **titre**, **nom du lieu**, **date** (jour seul, sans heure), **latitude**, **longitude**.
2. Pas de brouillon : une galerie est visible dès sa création.
3. Tri : date de la galerie décroissante ; à date égale, la plus récemment créée en premier.
4. Pagination : **10 galeries par page** (configurable).
5. Exemple d'intitulé affiché : « 10 octobre — Tokyo — Palais impérial ».

### 3.2 Contenu d'une galerie

6. Suite ordonnée d'éléments de trois types : **photo**, **vidéo**, **bloc de texte**. Ordre par défaut : ordre d'ajout.
7. Texte en **markdown limité** (paragraphes, gras, italique, listes, liens). **HTML brut interdit**. Le rendu est fait côté serveur.
8. Formats acceptés : **JPEG, PNG, WebP** (photos) et **MP4** (vidéos). La vérification porte sur le contenu réel du fichier, pas sur l'extension. Un refus affiche un message clair.
9. Tailles maximales configurables (valeurs proposées : **30 Mo** par photo, **500 Mo** par vidéo).
10. Trois versions par photo : **miniature** (~400 px), **pleine page** (~1920 px), **originale**. L'orientation EXIF est appliquée avant redimensionnement.
11. **EXIF GPS** retiré des versions miniature et pleine page, conservé sur l'originale.
12. Pour une vidéo : miniature extraite par ffmpeg, pictogramme de lecture superposé, fichier original servi en lecture **et** en téléchargement.
13. Supprimer un média efface ses fichiers sur disque **et** sa ligne en base. Supprimer une galerie supprime tout son contenu, après confirmation. Aucun fichier orphelin ne doit subsister.

### 3.3 Trajet en temps réel

14. Un point contient : latitude, longitude, source (`device` / `adsb` / `ais`), date/heure, et éventuellement le trajet suivi auquel il se rattache.
15. Validation : latitude entre -90 et 90, longitude entre -180 et 180, token obligatoire.
16. La date/heure est celle fournie par l'appareil, sinon celle du serveur. Les envois sont **idempotents** : un point déjà reçu (même source, même heure) est ignoré sans erreur (GPSLogger renvoie les points en rafale après une coupure réseau).
17. Affichage : un style par source (couleur, pointillés). Pour `device`, la ligne est **coupée** quand deux points consécutifs sont espacés de plus de X heures (défaut : 6 h). Pour `adsb` et `ais`, un trajet suivi correspond à une ligne.
18. L'admin peut modifier ou supprimer un point individuellement.
19. Position **publique, affichage immédiat** (usage familial, choix assumé).

### 3.4 Trajets suivis (vols et traversées)

20. Champs : **type** (avion / bateau), **libellé** libre, **date/heure théorique de départ**, **identifiant** (callsign pour un avion ; MMSI pour un bateau, 9 chiffres contrôlés), **statut** (`planifié`, `en cours`, `terminé`).
21. La date/heure de départ est **saisie en UTC** (le formulaire l'indique explicitement) et stockée en UTC.
22. Le futur batch fera évoluer le statut automatiquement (actif à l'heure de départ, terminé quand les données le signalent). L'admin peut aussi le forcer à la main.
23. Supprimer un trajet suivi demande si ses points de route sont supprimés avec lui ou conservés.
24. On peut ajouter des vols ou traversées à volonté via l'interface.

### 3.5 Accès et sécurité

25. Site public ouvert à tous. Tout ce qui est admin exige une session, sinon redirection vers la connexion.
26. Session longue (**7 jours** par défaut), protection CSRF, limitation des tentatives de connexion échouées.
27. **Non-indexation** à trois niveaux : `robots.txt`, balise `<meta name="robots" content="noindex">` et en-tête HTTP `X-Robots-Tag`.

---

## 4. Exigences non fonctionnelles

**Sécurité**
- Mots de passe admin en **bcrypt** dans la configuration ; token d'API comparé en temps constant ; aucun secret dans le dépôt (fichier de configuration externe monté dans le conteneur).
- Cookie de session `HttpOnly`, `Secure`, `SameSite`.
- Noms de fichiers stockés générés (UUID), jamais ceux fournis par l'utilisateur (protection contre les attaques par chemin).
- En-têtes de sécurité : CSP, `X-Content-Type-Options`, `Referrer-Policy`.

**Vie privée**
- Aucun traceur, aucune statistique, donc pas de bandeau cookies. Seul cookie : la session admin.

**Performance et mobile**
- Chargement différé des miniatures, cache HTTP long sur les médias.
- Peu d'animations, pas de bibliothèque d'animation.
- Mémoire maîtrisée (voir §7.3).

**Exploitation**
- Sauvegarde : dump MariaDB + répertoire des médias, via les outils Synology.
- Journaux sur la sortie standard de Docker ; endpoint de santé pour les healthchecks.
- Horodatages en **UTC** en base, affichage en heure locale côté navigateur. La date d'une galerie reste un jour simple.
- Base de données en **utf8mb4** de bout en bout.
- Navigateurs : versions récentes de Chrome, Safari, Firefox, Edge (mobile et PC).
- Tests ciblés sur les parties à risque (validation des uploads, pagination, API de positions).

---

## 5. Stack technique

> Versions vérifiées le 01/10/2026 : Spring Boot 4.1.1, Vue 3.5.43 (3.6 en RC), Vue Router 5.3.1, Vite 8.3.1, Node 24 pour le build. Starters Spring Boot 4 : `webmvc` (et non `web`) et `flyway` (obligatoire).

### 5.1 Backend

| Élément | Choix |
|---|---|
| Langage | Java 25 (LTS) |
| Framework | Spring Boot **4.1.1** |
| Modules | Web MVC, Security, Data JPA, Validation, Actuator (health uniquement) |
| Base | MariaDB (existante sur le NAS), utf8mb4 |
| Migrations | Flyway (`V1__init.sql`, …) |
| Markdown | commonmark-java (rendu serveur, HTML brut échappé) |
| Images / vidéos | Code existant de l'autre projet (upload, miniatures, ffmpeg), à adapter |
| Build | Maven (`fr.sanglierlab:travel-tracker:2.0.0`) |

- **Pas d'extension spatiale** : latitude et longitude sont des colonnes `DECIMAL(9,6)`.
- **Authentification** : session Spring Security, comptes dans la configuration. Token d'API dans l'en-tête `X-API-Token`.

### 5.2 Frontend

| Élément | Choix |
|---|---|
| Framework | Vue 3.5.x (stable) |
| Build | Vite 8.x |
| Routage | Vue Router 5 |
| État | Pas de Pinia : un composable suffit |
| Carte | Leaflet 1.9.x + tuiles OpenStreetMap |
| Langage | JavaScript (pas de TypeScript) |
| CSS | Un CSS général + un fichier de thème (variables uniquement), sans framework CSS |

- Application unique : les routes admin sont **chargées à la demande**.
- Téléchargement de l'originale : lien `<a download>` (même origine).
- Dépendances runtime : Vue, Vue Router, Leaflet.

### 5.3 Infrastructure

- Hébergement : **Synology DS220+** (Intel, amd64, 6 Go de RAM), Docker.
- Accès : sous-domaine derrière **HAProxy** (sur le NAS).
- Deux conteneurs orchestrés par `docker-compose.yml` :
  1. **`api`** : JAR Spring Boot sur image Java légère, avec **ffmpeg** installé.
  2. **`web`** : nginx qui sert l'application Vue compilée, relaie `/api` vers `api`, et sert **directement** `/media/` depuis un volume en lecture seule (Range supporté pour la vidéo).
- HAProxy ne pointe que vers `web` : **même origine**, donc pas de CORS et cookies simples.
- **Réseau** : les deux conteneurs sont en `network_mode: host` (MariaDB est sur `127.0.0.1:3306` du NAS, invisible depuis un réseau bridge). `api` écoute sur `127.0.0.1:18080` (non exposé), `web` sur le port **18081**.
- Les Dockerfiles sont dans `docker/` (`api.Dockerfile`, `web.Dockerfile`), le contexte de build est la racine du dépôt.
- Volumes : médias, fichier de configuration, fichier de thème CSS, (MariaDB déjà en place).
- HAProxy à régler pour les gros uploads (taille de corps et timeouts).

### 5.4 Structure du dépôt

```
travel-tracker/
├── SPECIFICATIONS.md
├── backend/      (Maven, fr.sanglierlab.traveltracker)
├── frontend/     (Vue + Vite)
└── docker/       (docker-compose.yml, nginx, Dockerfiles, exemples de config)
```

---

## 6. Architecture

```
Navigateur ──► HAProxy ──► conteneur web (nginx)
                             ├─ /          → application Vue compilée
                             ├─ /media/*   → fichiers (volume en lecture seule)
                             └─ /api/*     → conteneur api (Spring Boot + ffmpeg)
                                                ├─ MariaDB
                                                └─ volume médias (lecture/écriture)
GPSLogger ──► /api/track/points (token)
```

### 6.1 Découpage du backend

Package racine `fr.sanglierlab.traveltracker` :

- `config` : propriétés, sécurité, en-têtes, noindex.
- `gallery` : galeries et éléments (entités, services, contrôleurs public et admin).
- `media` : stockage, miniatures, extraction vidéo.
- `track` : points de route, trajets suivis, API d'ingestion.
- `auth` : connexion / déconnexion par session.

### 6.2 Modèle de données

**`gallery`**

| Colonne | Type | Remarque |
|---|---|---|
| id | BIGINT, PK | |
| title | VARCHAR(200) | obligatoire |
| place_name | VARCHAR(200) | obligatoire |
| gallery_date | DATE | jour seul |
| latitude, longitude | DECIMAL(9,6) | obligatoires |
| created_at, updated_at | DATETIME (UTC) | |

Index : (gallery_date DESC, id DESC).

**`gallery_item`**

| Colonne | Type | Remarque |
|---|---|---|
| id | BIGINT, PK | |
| gallery_id | FK, ON DELETE CASCADE | |
| sort_order | INT | ordre dans la galerie (`position` est un mot-clé SQL, évité) |
| type | PHOTO / VIDEO / TEXT | |
| text_markdown | TEXT | pour TEXT uniquement |
| file_key | CHAR(36) | UUID de stockage (médias) |
| original_filename | VARCHAR(255) | nom proposé au téléchargement |
| extension | VARCHAR(10) | |
| size_bytes, width, height | | dimensions pour réserver la place à l'affichage |
| created_at | DATETIME | |

**`tracked_trip`**

| Colonne | Type | Remarque |
|---|---|---|
| id | BIGINT, PK | |
| type | PLANE / BOAT | |
| label | VARCHAR(200) | libre |
| scheduled_departure | DATETIME (UTC) | |
| identifier | VARCHAR(20) | callsign ou MMSI (9 chiffres) |
| status | PLANNED / ACTIVE / FINISHED | |
| created_at, updated_at | DATETIME | |

**`track_point`**

| Colonne | Type | Remarque |
|---|---|---|
| id | BIGINT, PK | |
| source | DEVICE / ADSB / AIS | |
| latitude, longitude | DECIMAL(9,6) | |
| recorded_at | DATETIME(3) (UTC) | |
| trip_id | FK nullable | vers `tracked_trip` |
| created_at | DATETIME | |

Contrainte d'unicité sur (source, recorded_at, latitude, longitude) pour l'idempotence (déjà dans la migration V1) ; index sur trip_id. Supprimer un trajet suivi détache ses points (`ON DELETE SET NULL`), le service les supprime si l'admin le demande.

Les comptes admin et le token d'API n'ont **pas** de table : ils sont dans la configuration.

### 6.3 Stockage des fichiers

```
media/{galerie}/{uuid}-thumb.jpg      miniature (~400 px), photo et vidéo
media/{galerie}/{uuid}-display.jpg    pleine page (~1920 px), photo uniquement
media/{galerie}/{uuid}-original.{ext} original (photo) ou vidéo MP4 (lecture + téléchargement)
```

Supprimer une galerie supprime son dossier ; supprimer un média supprime ses fichiers puis sa ligne.

### 6.4 API

**Publique (lecture seule)**

| Endpoint | Rôle |
|---|---|
| `GET /api/public/galleries?page=n` | Une page de galeries avec leurs éléments (markdown déjà rendu en HTML sûr) ; renvoie aussi `pageSize`. |
| `GET /api/public/map` | Toutes les galeries en version légère (id, titre, lieu, date, coordonnées, miniature de couverture, **numéro de la page** où elles apparaissent), même tri que la liste. |
| `GET /api/public/track` | Tous les trajets, déjà découpés en segments avec leur source. |

Le numéro de page de chaque galerie est calculé côté serveur (`rang / taille de page + 1`) : le front n'a pas besoin de connaître la taille de page. L'adresse `/galerie/:id` est la seule source de vérité de la sélection : sélectionner une galerie (depuis la carte ou la liste) change l'adresse.

**Authentification**

| Endpoint | Rôle |
|---|---|
| `POST /api/auth/login` | Connexion |
| `POST /api/auth/logout` | Déconnexion |
| `GET /api/auth/me` | État de la session |

**Admin (session + CSRF)**

| Domaine | Endpoints |
|---|---|
| Galeries | `GET`, `POST`, `PUT`, `DELETE /api/admin/galleries[/{id}]` |
| Éléments | `POST /api/admin/galleries/{id}/items/media` (un fichier par appel), `POST .../items/text`, `PUT` / `DELETE /api/admin/items/{id}`, `POST /api/admin/items/{id}/move` |
| Trajets suivis | `GET`, `POST`, `PUT`, `DELETE /api/admin/trips[/{id}]` (`?deletePoints=true/false`) |
| Points | `GET /api/admin/points` (filtres source, trajet, pagination), `GET`, `PUT`, `DELETE /api/admin/points/{id}` |
| Config | `GET /api/admin/info` (lecture seule) |

**API de positions (en-tête `X-API-Token`)**

- `POST /api/track/points` : `latitude`, `longitude` et `recordedAt` (l'heure du point, pas celle de l'envoi). Réponse **200** avec un petit JSON.
- La source est toujours `DEVICE` ; les points `ADSB` / `AIS` seront insérés par le futur batch interne.
- Configuration GPSLogger (URL personnalisée, en-tête, corps JSON avec les variables de position et d'heure) : à documenter à la phase 4 après vérification de la documentation de GPSLogger.

### 6.5 Frontend : routes et écrans

**Public**
- `/` et `/?page=2` : écran principal carte + liste.
- `/galerie/:id` : même écran, galerie ciblée sélectionnée.
- `/connexion`.
- Visionneuse photo/vidéo : surcouche inscrite dans l'historique du navigateur (pas une route).

**Admin (chargé à la demande)**
- `/admin` : menu à grosses icônes.
- `/admin/galeries`, `/admin/galeries/nouvelle`, `/admin/galeries/:id`.
- `/admin/trajets`, `/admin/trajets/nouveau`, `/admin/trajets/:id`.
- `/admin/points`, `/admin/points/:id`.
- `/admin/config`.

**Comportement de l'écran principal**
- **Grand écran** : deux colonnes plein écran, carte fixe à gauche, liste défilante à droite, largeur de lecture raisonnable, davantage de colonnes de miniatures si la place le permet. Pas de « smartphone au milieu d'un écran géant ».
- **Mobile** : carte en haut (~40 % de la hauteur) avec un bouton pour l'agrandir ; seule la liste défile en dessous.
- **Timeline** : ligne verticale reliant les galeries, un nœud par galerie avec sa date ; photos consécutives en grille, interrompue par les blocs de texte.
- **Marqueurs** : pictogramme identique pour toutes les galeries, galerie sélectionnée mise en évidence, petite vignette de couverture dans l'infobulle.
- **Trajets** : `device` en trait plein, `adsb` en pointillés, `ais` en tirets espacés, une couleur par source.

**Thème** : `style.css` (mise en page) + `theme.css` (variables : couleurs, polices, arrondis), ce dernier placé dans un volume Docker pour être modifié sans reconstruire l'image.

---

## 7. Configuration serveur

### 7.1 `application.yml` externe (clés prévues)

- Comptes admin : identifiant + hash bcrypt.
- Token d'API.
- Dossier des médias.
- Tailles maximales (photo, vidéo).
- Taille de page.
- Seuil de coupure des traces `device` (heures).
- Durée de session.
- Chemin de ffmpeg.

### 7.2 Réseau

- HAProxy : taille de corps et timeouts élevés sur la route d'upload.

### 7.3 Mémoire (DS220+, 6 Go)

- Tas JVM limité (~1 Go) + limite mémoire sur le conteneur.
- **Pas de `byte[]` pour les fichiers entiers** : flux (`InputStream`) et fichiers temporaires sur disque. L'upload d'une vidéo de 500 Mo ne doit pas coûter 500 Mo de RAM.
- Décodage d'images **sérialisé** (une photo à la fois), contrôle des dimensions en pixels avant décodage.

---

## 8. Plan de réalisation

Chaque phase se termine par quelque chose qui tourne et se teste.

| Phase | Contenu | Résultat |
|---|---|---|
| **0 — Socle** | Structure du dépôt, `pom.xml`, projet Vite, `docker-compose.yml`, nginx, Flyway (schéma complet), `application.yml`, sécurité, noindex | Le site démarre sur le NAS, connexion admin OK, écran vide |
| **1 — Galeries et médias (backend)** | Entités, services, API galeries et éléments, markdown, réordonnancement, **intégration du code existant** (upload, miniatures, ffmpeg) | Tout se teste en API (curl / Postman) |
| **2 — Site public** | Carte + liste, timeline, pagination, synchronisation, `/galerie/:id`, visionneuse, téléchargement, CSS général + thème | Le site se consulte |
| **3 — Interface admin** | Menu, création/édition de galeries (mini-carte, « Me positionner »), upload un par un avec progression, blocs de texte, monter/descendre, suppressions | Utilisable en voyage pour les galeries |
| **4 — Trajet en temps réel** | Endpoint GPSLogger, stockage, tracé par source avec coupures, pages admin des points | Le parcours s'affiche en direct |
| **5 — Trajets suivis** | CRUD vols/traversées, contrôle MMSI, statuts, choix à la suppression | Stockage et configuration prêts pour le futur batch |
| **6 — Finitions et production** | Images Docker finales, limites mémoire, healthchecks, HAProxy, sauvegardes, tests sur mobile réel, tests ciblés | Mise en production |

### Découpage fin des phases 2 et 3

Les phases 2 à 5 sont livrées en petites étapes, chacune testable seule :

| Étape | Contenu |
|---|---|
| **2a** | Écran principal : mise en page (carte / liste), liste paginée, timeline, grilles de miniatures, textes, pastille de lecture des vidéos |
| **2b** | Carte Leaflet : marqueurs, synchronisation marqueur/liste avec changement de page, adresse `/galerie/:id` |
| **2c** | Visionneuse plein écran (photo et vidéo), bouton de téléchargement, bouton « retour » du téléphone |
| **2d** | Finitions : carte agrandissable sur mobile, thème, réglages responsive |
| **3a** | Admin : liste des galeries, création/modification avec mini-carte et « Me positionner », suppression |
| **3b** | Admin : envoi de médias un par un (progression, « Réessayer »), suppression de médias |
| **3c** | Admin : blocs de texte, boutons monter/descendre |

### À fournir au début de la phase 1

- Les classes d'upload et de gestion de fichiers, de création de miniatures et d'extraction vidéo (ffmpeg).
- Les dépendances Maven correspondantes.
- La façon dont ffmpeg est appelé (chemin, options) et le `Dockerfile` de l'autre projet, s'il existe.

---

## 9. Évolutions possibles (hors périmètre actuel)

- Likes et commentaires.
- Transcodage des vidéos HEVC/.mov en MP4/H.264 (ffmpeg déjà présent).
- Batch ADS-B (callsign) et AIS (MMSI), activé à l'heure de départ et arrêté quand les données le signalent.
- Regroupement de marqueurs si la carte devient illisible.
- Simplification des tracés si le nombre de points devient élevé.

---

## 10. Décisions d'implémentation (phase 1)

- **Formats** : détectés sur les premiers octets du fichier, jamais sur l'extension ni le type MIME envoyé par le client. JPEG, PNG, WebP, MP4. Le QuickTime (`.mov`), l'HEIC et le reste sont refusés.
- **WebP** : converti en JPEG par ffmpeg avant redimensionnement (Java ne lit pas le WebP) ; l'original WebP est conservé tel quel.
- **Pas de détection de doublons** (hash) : non demandée, et l'envoi un par un avec « Réessayer » ne crée pas de doublon puisque les fichiers d'un envoi échoué sont supprimés.
- **Images** : la photo est décodée une seule fois ; la miniature (400 px) est calculée à partir de l'image pleine page (1920 px). Jamais d'agrandissement. Refus au-delà de 100 mégapixels, contrôlé avant le décodage. Les JPEG produits ne contiennent aucune métadonnée (donc pas de GPS).
- **`width` / `height`** : dimensions de l'image pleine page (photo) ou de la miniature (vidéo), pour réserver la place. Pour une vidéo, `NULL` signifie « pas de miniature » (échec ffmpeg) : la vidéo est conservée plutôt que de perdre l'envoi.
- **Mémoire** : les fichiers ne passent jamais par un `byte[]` ; l'upload est écrit sur disque puis traité à partir du fichier. Les traitements d'images et les appels ffmpeg sont sérialisés. Pas de transaction ouverte pendant un envoi.
- **Suppression** : les fichiers ne sont effacés qu'après validation (commit) de la suppression en base.
- **Markdown** : paragraphes, gras, italique, listes, liens. Titres, citations, code, séparateurs et images sont désactivés ; HTML brut échappé ; liens en `target="_blank" rel="noopener noreferrer nofollow"`.
- **API publique** : `GET /api/public/galleries?page=n` (n à partir de 1, taille de page lue dans la configuration) et `GET /api/public/map` (toutes les galeries, avec miniature de couverture).
- **Réordonnancement** : `POST /api/admin/items/{id}/move?direction=UP|DOWN` échange l'élément avec son voisin.
- **Erreurs** : corps JSON `{"message": "..."}` en français.
- **Développement** : le profil Spring `dev` sert `/media/**` depuis le dossier des médias (en production, c'est nginx).

## 11. Décisions d'implémentation (phase 2)

- **Sélection pilotée par l'adresse** : `/?page=n`, `/galerie/:id` (galerie sélectionnée, page ouverte, carte centrée) et `?media=:id` (visionneuse ouverte). L'adresse est l'unique source de vérité ; Leaflet est chargé à part.
- **Visionneuse** : ouvre une entrée d'historique (le bouton « retour » du téléphone la ferme et ramène exactement où l'on était) ; passer d'un média à l'autre remplace l'entrée au lieu d'en ajouter. Un lien `?media=` reste partageable ; un média inconnu est ignoré.
- **Contenu** : photos affichées en version « pleine page » (la miniature, déjà en cache, apparaît pendant le chargement) ; vidéos lues depuis le fichier original (`<video>` natif, lecture automatique) ; téléchargement de l'original par un lien `download` (même origine) avec son nom d'origine et sa taille.
- **Navigation** : boutons précédent/suivant, flèches du clavier, balayage au doigt (photos seulement, désactivé quand l'image est zoomée), Échap pour fermer. Les photos voisines sont préchargées. Les blocs de texte sont ignorés dans la visionneuse.
- **Vidéo illisible** (ex. HEVC non supporté par le navigateur, puisqu'il n'y a pas de transcodage) : message explicatif et téléchargement possible.
- **Accessibilité** : fenêtre modale (`role="dialog"`), focus placé puis restitué, Tab qui reste dans la fenêtre.
- **Carte agrandissable (mobile et tablette)** : un bouton passe la carte en plein écran, la liste est masquée mais conserve sa position. Choisir un marqueur réduit la carte et montre la galerie. En paysage sur téléphone, carte et liste passent côte à côte.
- **Animations réduites** : pas de fondu des tuiles ni d'animation des marqueurs ; aucune animation de zoom si le visiteur demande de réduire les animations. Repli `vh` pour les navigateurs sans `dvh`.
- **Thème** : `frontend/public/theme.css` est le thème par défaut ; `frontend/themes/japon.css` est un exemple complet (mêmes variables). Le titre de l'onglet reflète la galerie sélectionnée.

## 12. Décisions d'implémentation (phase 3a : administration des galeries)

- **Routes** : `/admin/galeries` (liste), `/admin/galeries/nouvelle`, `/admin/galeries/:id` (modification). Toutes chargées à la demande et protégées par la session ; une réponse 401 (session expirée) renvoie vers la connexion, puis revient sur la page en cours.
- **Navigation admin** : barre fixe en bas sur mobile (cibles de 56 px, marges de sécurité), barre en haut sur grand écran. Les entrées Trajets, Points et Config s'ajouteront avec leurs phases.
- **Position** : carte où l'on touche pour placer le point, qu'on peut faire glisser ; bouton « Me positionner » (géolocalisation du navigateur, avec précision affichée et messages clairs en cas de refus, de délai dépassé ou d'indisponibilité) ; saisie manuelle des coordonnées en secours. À la création, la carte est centrée près de la dernière galerie. Le point est obligatoire.
- **Création** : après l'enregistrement, on arrive sur la page de modification de la nouvelle galerie (où le contenu s'ajoutera aux étapes 3b et 3c).
- **Suppression** : boîte de confirmation native (`<dialog>`) qui annonce le contenu supprimé (photos, vidéos, textes) et le caractère définitif ; les fichiers du serveur sont effacés avec la galerie.
- **Saisie** : tous les caractères sont acceptés (japonais, etc.) sans traduction ; la date du jour est proposée à la création.

## 13. Décisions d'implémentation (phase 3b : envoi et suppression de médias)

- **Envoi** : `XMLHttpRequest` (et non `fetch`, qui ne sait pas mesurer l'envoi) pour une vraie progression par fichier. Quand les octets sont partis, l'état passe à « Traitement sur le serveur… » jusqu'à la réponse (miniatures, ffmpeg).
- **File d'attente** : les fichiers partent **un par un**. Un échec n'arrête pas les suivants ; chaque fichier en échec affiche le message du serveur (format refusé, fichier trop volumineux, session expirée, réseau) avec « Réessayer » ou « Retirer », et un « Tout réessayer » est proposé. Un envoi en cours peut être annulé (il est interrompu net, sans erreur). Pas de reprise d'un envoi partiel.
- **Garde-fous** : avertissement du navigateur si l'onglet se ferme, et confirmation si l'on quitte la page pendant un envoi (qui est alors interrompu). L'écran est maintenu allumé pendant les envois (Wake Lock, quand le navigateur le permet).
- **Zone d'envoi** : bouton pour le téléphone (sélection multiple, formats JPEG/PNG/WebP/MP4), glisser-déposer sur ordinateur.
- **Liste du contenu** : éléments dans l'ordre d'affichage avec miniature (qui ouvre l'image en grand), nom d'origine et taille ; vidéo sans miniature signalée ; extrait pour les textes. Suppression d'un élément avec confirmation (fichiers effacés du serveur).
- **Limites** : la taille maximale d'un fichier est contrôlée par le serveur (30 Mo par photo, 500 Mo par vidéo par défaut), qui répond par un message clair. Une application mise en arrière-plan par le téléphone peut suspendre l'envoi : garder l'écran allumé et l'onglet au premier plan.

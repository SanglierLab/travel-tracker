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
| `GET /api/public/feed?page=n` | **Le fil** : une page d'entrées `{ type: GALLERY, gallery }` ou `{ type: STAGE, stage }` (albums + étapes commencées, date décroissante, album avant étape à date égale ; `pageSize` éléments par page, albums et étapes comptés ensemble). |
| `GET /api/public/feed/positions` | `{ today, upcomingDays, stages: { id: page } }` : date du jour du serveur, réglage `upcoming-days` et page de chaque étape commencée dans le fil (les étapes à venir en sont absentes). |
| `GET /api/public/galleries?page=n` | (ancien, albums seuls) Une page de galeries avec leurs éléments (markdown déjà rendu en HTML sûr) ; renvoie aussi `pageSize`. |
| `GET /api/public/map` | Toutes les galeries en version légère (id, titre, lieu, date, coordonnées, miniature de couverture, **numéro de la page** où elles apparaissent dans le fil mélangé), même tri que la liste. |
| `GET /api/public/track` | Lignes à dessiner (source, trajet, heures de début et de fin, points `[latitude, longitude]` simplifiés) et dernière position connue. |

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

- `POST /api/track/points` : `latitude`, `longitude` et l'heure du point (`time` ISO 8601, ou `timestamp` en secondes ; pas l'heure de l'envoi). Réponse **200** avec `{"status":"created"}` ou `{"status":"duplicate"}`.
- La source est toujours `DEVICE` ; les points `ADSB` / `AIS` seront insérés par le futur batch interne.
- Configuration de GPSLogger (URL, en-têtes, corps JSON, méthode) : voir `README.md`.

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

## 14. Décisions d'implémentation (phase 3c : textes et ordre du contenu)

- **Blocs de texte** : ajout (« Ajouter un texte », en dernière position) et modification (crayon sur la ligne) dans une fenêtre d'édition. Saisie en markdown limité (voir §10), 20 000 caractères maximum, tous caractères acceptés.
- **Mise en forme sur téléphone** : boutons Gras, Italique, Liste à puces et Lien qui entourent la sélection (ou insèrent un modèle) ; pour un lien, le curseur est placé directement sur l'adresse.
- **Aperçu** : calculé par le serveur (`POST /api/admin/markdown/preview`) avec le même rendu que la partie publique : ce que l'on voit est ce que verront les visiteurs. Pas de bibliothèque markdown côté navigateur.
- **Abandon** : une confirmation est demandée si le texte a été modifié ; elle ne l'est pas sinon.
- **Ordre** : boutons Monter / Descendre sur chaque ligne (photos, vidéos et textes se mélangent librement), inactifs aux extrémités et pendant un déplacement. Le serveur échange l'élément avec son voisin ; l'écran fait de même sans recharger la galerie, et l'élément déplacé reste visible.
- **Hors périmètre** : glisser-déposer pour réordonner (moins fiable sur mobile), insertion d'un texte à une position précise (on l'ajoute en dernier, puis on le monte).

## 15. Décisions d'implémentation (phase 4a : réception des positions)

- **GPSLogger** (v136, vérifiée dans sa documentation et ses notes de version) : envoi par `POST` JSON vers une URL personnalisée, avec en-têtes et corps libres. Corps retenu : `{"latitude":%LAT,"longitude":%LON,"time":"%TIME"}` ; en secours `"timestamp":%TIMESTAMP`. Les champs inconnus sont ignorés.
- **Authentification** : en-tête `X-API-Token` comparé en temps constant (empreintes SHA-256) ; refus 401 avec un message en français. Le token ne passe jamais dans l'URL. La route ne crée pas de session et échappe au contrôle CSRF.
- **Heure** : celle du point (GPSLogger peut renvoyer des points en rafale après une coupure), sinon celle de réception. Une date à plus d'un jour dans le futur (horloge du téléphone fausse) est refusée ; une date sans fuseau horaire aussi (on ne devine pas).
- **Idempotence** : un point déjà reçu (même source, heure à la milliseconde, coordonnées arrondies à 6 décimales) est ignoré avec un statut 200 « duplicate » ; la contrainte d'unicité de la base tranche en cas d'envois simultanés.
- **Source** : toujours `DEVICE` pour cette route ; les points `ADSB` et `AIS` seront insérés par le futur batch interne.
- **Messages d'erreur** : un corps non JSON ou un `Content-Type` manquant donnent un message explicite (utile pour régler GPSLogger).

## 16. Décisions d'implémentation (phase 4b-1 : tracé côté serveur)

- **Endpoint** : `GET /api/public/track` (public, lecture seule) renvoie `{ segments: [...], last: {...} }`. Chaque segment porte sa `source` (`DEVICE`, `ADSB`, `AIS`), son `tripId` éventuel, ses heures de début et de fin (UTC) et ses points `[latitude, longitude]`. `last` est la dernière position connue, toutes sources confondues (ou `null`). `lateAfterSeconds` donne, par source, le délai sans position au-delà duquel `last` est « en retard » (`app.track-late-after.device|adsb|ais`, défauts 10 min, 5 min, 20 min).
- **Découpage** : les points du téléphone forment une ligne, coupée quand deux points consécutifs sont espacés de plus de `app.track-gap-hours` (6 h par défaut ; un écart exactement égal au seuil ne coupe pas). Les points d'un trajet suivi (vol, traversée) forment une seule ligne par trajet, sans coupure. Un point isolé donne une ligne d'un seul point. Les lignes sont triées de la plus ancienne à la plus récente.
- **Allègement** : tremblement du GPS à l'arrêt supprimé (points à moins de 5 m du précédent), puis algorithme de Douglas-Peucker avec 25 m de tolérance. Si le total dépasse 20 000 points, la tolérance double jusqu'à rentrer dans le budget ; en dernier recours, points régulièrement espacés (extrémités conservées). Mesure : 10 800 points sur 15 jours -> 678 points renvoyés, calcul en 40 à 250 ms.
- **Ligne de changement de date** : les longitudes d'une ligne sont continues (elles peuvent dépasser ±180) pour qu'un vol transpacifique ne soit pas tracé d'un bord à l'autre de la carte.
- **Pas de cache** pour l'instant (usage familial) : le tracé est recalculé à chaque appel. À ajouter si besoin.

## 17. Décisions d'implémentation (phase 4b-2 : tracé sur la carte)

- **Style par source** (`frontend/src/trackStyle.js`) : téléphone en **trait plein**, avion en **pointillés** ronds, bateau en **tirets espacés**. Les couleurs viennent des variables `--track-device`, `--track-adsb`, `--track-ais` du thème, lues au moment du dessin : un autre thème les change sans toucher au code. Les styles de trait, eux, sont fixes, pour que les sources se distinguent même sans la couleur.
- **Éléments** : une ligne par segment, avec une infobulle (source et période, dans le fuseau du visiteur) ; un point isolé (premier point après une longue pause) est une petite pastille ; la **dernière position connue** a un marqueur propre à sa source (`liveIcon`) : gros point à bordure blanche (téléphone), ou picto seul, sans étiquette, de la couleur du tracé, avec un contour blanc (bateau pour AIS, avion pour ADS-B). L'avion est tourné selon son cap : angle à l'écran (projection de Mercator, `headingDegrees`) entre la dernière position et le dernier point du tracé de la même source distant d'au moins 30 m (`lastHeading`) ; sans cap (un seul point), l'avion pointe vers le nord comme son picto. Infobulle sur trois lignes au plus : « Dernière position · source », « il y a 12 min », et « En retard : pas de nouvelle position » le cas échéant.
- **« En retard »** : la page compare l'heure de la dernière position à l'heure courante (le tracé est rechargé chaque minute) avec le délai de sa source ; au-delà, le marqueur passe en gris (variable de thème `--track-late`, présente dans les deux thèmes) et estompé, le point rond avec un contour pointillé, et la légende ajoute « Position en retard ». Valeurs de repli côté page si le serveur n'envoie rien : 10, 5 et 20 minutes.
- **Légende** en bas à gauche de la carte, limitée aux sources réellement présentes.
- **Temps réel** : le tracé est rechargé toutes les minutes tant que l'onglet est visible, et dès qu'il le redevient. En cas d'échec du chargement, l'affichage précédent reste en place et le prochain rafraîchissement réessaie.
- **Cadrage initial** : la carte se cadre une seule fois, quand les galeries sont connues (même s'il n'y en a pas), sur l'ensemble des galeries et du tracé ; les rafraîchissements suivants ne bougent plus la carte.
- **Les marqueurs de galeries restent au-dessus du tracé** et gardent leur comportement (sélection, infobulle).

## 18. Décisions d'implémentation (phase 4c-1 : filtrage des points à l'ajout)

- **Constat** : un téléphone peut poser un point à des dizaines de kilomètres de la réalité (localisation « réseau » ou périmée), puis reprendre un fix correct, ce qui rend le trajet chaotique. Le filtrage se fait en trois couches : réglages de GPSLogger (voir `README.md`), règles « sans mémoire » à l'ajout (cette étape), filtre de pics à l'affichage (étape 4c-2).
- **Précision** : GPSLogger envoie `"accuracy":"%ACC"` ; le champ est volontairement un texte, car une valeur vide rendrait le JSON invalide. « Vide », « 0 », négatif ou illisible = précision inconnue. Une virgule décimale est acceptée.
- **Règles à l'ajout** (points du téléphone seulement ; ADS-B et AIS sont considérés fiables) : un point est **écarté et non enregistré** si sa précision annoncée dépasse `app.track-max-accuracy-meters` (100 m par défaut, limite incluse, valeur comparée après arrondi à 0,1 m) ou si sa position est 0°/0° (GPS sans position ; l'équateur et le méridien de Greenwich restent valables). Une précision inconnue est acceptée.
- **Réponse** : toujours HTTP 200, `{"status":"created"|"duplicate"|"ignored","reason":null|"accuracy"|"no-fix"}`, pour que GPSLogger ne réessaie pas un point écarté volontairement. Un point écarté est journalisé.
- **Stockage** : migration `V2__track_point_accuracy.sql` ajoute `accuracy_meters DECIMAL(7,1) NULL` à `track_point` (NULL pour les points existants) : la précision sert à régler les filtres plus tard.
- **Pourquoi pas de filtre de pics à l'ajout** : confirmer qu'un point isolé est faux demande les points suivants, qui n'existent pas encore à ce moment ; GPSLogger peut en outre renvoyer des points en rafale et dans le désordre après une coupure, et une décision prise à l'ajout est irréversible. Ce filtre se fait donc à l'affichage (4c-2), sans rien supprimer en base.
- **Filtre de pics à l'affichage (4c-2, `SpikeFilter`)** : appliqué par `TrackQueryService` avant le découpage en lignes ; rien n'est supprimé en base. Il ne concerne que les points du téléphone (ADS-B et AIS sont fiables). Un point est suspect quand la vitesse implicite depuis le dernier bon point dépasse 350 km/h ; il est écarté si au moins 2 des 3 points suivants sont plus proches du dernier bon point que de lui, conservé sinon (vrai déplacement, par exemple après un vol téléphone éteint : sur un long intervalle la vitesse implicite reste faible et le point n'est même pas suspect). Quand les points suivants manquent pour trancher (dernier ou avant-dernier point du direct), le point suspect est masqué 30 min au plus, puis conservé ; la dernière position affichée est donc toujours un point retenu. Le premier point est écarté si le deuxième est inatteignable mais cohérent avec le troisième. Limite connue : une série d'au moins trois mauvais points consécutifs ressemble à un vrai déplacement et est conservée. Constantes dans `SpikeFilter` (350 km/h, 3 points, 2 confirmations, 30 min).

## 19. Décisions d'implémentation (phase 5a : vols suivis et batch ADS-B)

- **Stockage** : la table `tracked_trip` existe depuis la V1 (type `PLANE`, libellé, départ, identifiant, statut) : aucune migration. Le libellé est fabriqué (« Vol AFR1234 »). Le départ est la **date** saisie, avec une heure UTC facultative (minuit UTC sans heure).
- **Numéro de vol** : mis en majuscules, espaces retirés, 2 à 8 lettres ou chiffres (« afr 1234 » → `AFR1234`). Selon le fournisseur choisi, il faudra peut-être l'indicatif radio plutôt que le numéro commercial.
- **API** (administration : session et CSRF) : `GET /api/admin/flights` (état, nombre de positions, dernière position), `POST` (enregistrer), `POST /{id}/start` (on), `POST /{id}/stop` (off), `DELETE /{id}`.
- **On / off** : manuel (pas de lancement automatique). « On » met le vol `ACTIVE` (depuis `PLANNED` ou `FINISHED`) et lance le batch ; « off » le met `FINISHED` et arrête le batch, en conservant les positions. **Un seul vol actif à la fois** : démarrer un autre vol est refusé (409) avec un message qui nomme le vol en cours.
- **Suppression** : refusée (409) tant que le suivi est actif ; elle supprime le vol **et ses positions** (utile pour nettoyer un essai).
- **Batch** (`AdsbBatch`) : un cycle toutes les `app.adsb-poll-interval` (30 s par défaut) sur un seul fil dédié. La base fait foi : chaque cycle relit le vol et s'arrête de lui-même s'il n'est plus actif ou a été supprimé ; le suivi d'un vol actif reprend après un redémarrage du serveur ; un cycle en échec est journalisé sans arrêter le suivi.
- **Intégration du fournisseur** : interface `FlightPositionProvider.findPosition(indicatif) → Optional<AdsbPosition>`, implémentée par `AdsbLolClient` (API adsb.lol, `GET /v2/callsign/{callsign}`). L'indicatif saisi est le callsign ICAO (`AFR186`, pas `AF186`). `Optional.empty()` = aucune position (hors de portée, pas parti, posé) : cycle sauté sans erreur. Positions de plus de 10 minutes ignorées ; position identique à la précédente non réenregistrée ; `now` (millisecondes) − `seen_pos` (secondes), arrondi à la seconde ; délais réseau 5 s / 10 s. Les points sont enregistrés avec la source `ADSB` et l'identifiant du vol : une ligne en pointillés par vol sur la carte, sans coupure ; les doublons sont ignorés.
- **Hors périmètre** : démarrage et arrêt automatiques (heure de départ, vol arrivé), traversées en bateau (AIS), modification d'un vol (le supprimer et le recréer).
- **Étape 5b** : le formulaire d'administration (« Vols ») qui utilise cette API.

## 20. Décisions d'implémentation (phase 5b : formulaire des vols)

- **Page** `/admin/vols` (menu d'administration : tuile « Vols » ; barre de navigation : entrée « Vols »), avec un formulaire d'ajout (numéro de vol, date proposée au jour d'aujourd'hui, heure UTC facultative) et la liste des vols.
- **Bouton on/off** : un vrai interrupteur accessible (`role="switch"`, cible de 48 px, sans animation) par vol, avec son état en toutes lettres (« Activé » / « Désactivé »). Pendant une action, les boutons sont bloqués pour éviter un double clic. Quand le serveur refuse (un autre vol est déjà suivi), son message est affiché tel quel et l'état réel du serveur est réaffiché.
- **Liste** : numéro de vol, date de départ (avec l'heure et « UTC » seulement si elle a été saisie), état (« Prévu », « Suivi en cours », « Suivi arrêté »), nombre de positions enregistrées et ancienneté de la dernière. Le vol suivi est mis en évidence aux couleurs de la source ADS-B de la carte.
- **Rafraîchissement automatique** toutes les 15 s, uniquement tant qu'un suivi est actif et que la page est ouverte ; un échec silencieux conserve l'affichage.
- **Suppression** : bloquée à l'écran pour le vol suivi ; sinon, confirmation qui annonce le nombre de positions supprimées avec le vol et précise que c'est définitif.

## 21. Étapes du voyage (phase 6)

### Fonctionnement validé

Une **étape** est un jalon du voyage, passé ou à venir : un **lieu fixe** (une position, pas de GPX) ou un **trajet** en train, avion ou bateau (position de début, position de fin, GPX facultatif). Elle a un nom, une date de début et une date de fin (des jours), et une place dans un ordre manuel.

- **Carte** : toutes les étapes sont affichées, y compris à venir (un peu plus pâles). Un marqueur de forme différente de l'épingle des albums (badge arrondi) ; un picto par type (lieu fixe, train, avion, bateau) ; un trajet est marqué au milieu de son tracé. Les albums gardent leur épingle, avec un appareil photo, qui sert aussi dans l'en-tête de leurs cartes du fil.
- **Tracé** : étapes reliées dans l'ordre manuel par un tracé fléché, épais, sous la trace GPS fine. Chaque type de trajet a sa couleur (variables `--stage-place|train|plane|boat`, dans les deux fichiers de thème) et son trait fixe (train : plein ; avion : gros points ; bateau : longs tirets), repris par la flèche, la pastille de la carte, la légende et le picto des cartes du fil ; `--itinerary` ne sert plus qu'aux liens entre étapes et de repli. Un trajet suit son GPX, sinon un arc de cercle départ → arrivée (écart de 15 % de la corde, porté par le grand cercle pour un avion), toujours sur la droite du sens de marche pour qu'un aller et son retour ne se superposent pas ; un lien en pointillés plus fins relie deux étapes consécutives distantes de plus d'un kilomètre.
- **Fil des photos** : les étapes déjà commencées s'insèrent comme intercalaires selon leur **date de début**, sous les albums du même jour, avec le picto de leur type ; la pagination compte albums et étapes ensemble. **Les étapes futures forment un bloc repliable** (bouton « voir la suite du voyage »), en tête du fil puisque celui-ci va du plus récent au plus ancien, pour ne pas repousser les dernières photos publiées ; ce bloc n'entre pas dans la pagination.
- **Administration** : une page « Étapes » (liste, ajout, suppression, monter/descendre, « trier par date », positionnement sur carte, envoi du GPX).
- **Interaction** : un clic sur un marqueur d'étape ou sur son intercalaire la met en évidence des deux côtés ; adresse `/etape/:id` comme pour les galeries.
- **Découpage** : 6a backend, 6b administration, 6c carte, 6d fil des photos.

### Décisions d'implémentation (phase 6a : backend)

- **Table `stage`** (migration `V3`) : nom, type (`PLACE`, `TRAIN`, `PLANE`, `BOAT`), dates de début et de fin (la fin ne précède pas le début ; un séjour d'une nuit a la même date), position de début, position de fin (nulle pour un lieu fixe), tracé GPX allégé, ordre manuel. Contraintes de cohérence (coordonnées, dates, début/fin de position toutes deux présentes ou absentes) également en base.
- **Règles** : une position de début est toujours obligatoire ; un trajet exige aussi une position de fin ; pour un lieu fixe, une position de fin envoyée est ignorée. Passer un trajet en lieu fixe efface sa fin et son tracé.
- **Ordre** : une nouvelle étape va en fin de liste ; monter/descendre échange avec la voisine et **renumérote tout** (l'ordre se répare de lui-même) ; « trier par date » trie par date de début puis de fin, en conservant l'ordre actuel à dates égales.
- **GPX** : `POST /api/admin/stages/{id}/gpx` (champ `file`, 10 Mo maximum, trajets seulement). Lecture en flux, sans bibliothèque, DTD et entités externes désactivées (un fichier piégé est refusé proprement). Points de piste (`trkpt`, toutes pistes et segments à la suite), sinon d'une route (`rtept`) ; les waypoints isolés sont ignorés ; points invalides ignorés. Le tracé est allégé (points à moins de 5 m supprimés, puis Douglas-Peucker à 50 m, tolérance relevée si besoin, 1 500 points au plus) et stocké sous forme compacte dans la colonne `path_data`. Longitudes continues à la ligne de changement de date. `useEndpoints=true` reprend les extrémités du tracé comme positions de début et de fin. Mesure : un GPX de 10 Mo (89 000 points) est lu en 0,4 à 1 s et réduit à 943 points.
- **API** : administration (`/api/admin/stages` : liste sans tracés, détail avec tracé, création, modification, suppression, `move`, `sort-by-date`, GPX envoi/retrait) ; public `GET /api/public/itinerary` (toutes les étapes dans l'ordre manuel, avec leur tracé).

### Décisions d'implémentation (phase 6b-1 : administration des étapes)

- **Pages** : `/admin/etapes` (liste), `/admin/etapes/nouvelle` et `/admin/etapes/:id` (formulaire). Entrée « Étapes » dans la barre de navigation (5 entrées) ; la tuile « Étapes » du menu remplace l'ancienne tuile « Points » (l'édition des points GPS a été abandonnée).
- **Liste** : dans l'ordre manuel (celui du tracé fléché). Chaque ligne montre le picto du type, le nom, le type et la période (« 1–5 mars 2026 », « 5 mars 2026 »), un badge « À venir » quand l'étape n'a pas commencé, et les boutons monter, descendre, modifier/positionner et supprimer (avec confirmation). Monter/descendre rechargent la liste dans l'ordre renuméroté par le serveur. « Trier par date » est proposé en permanence, et un avertissement le met en avant quand une étape commence avant la précédente.
- **Formulaire** : choix du type par quatre boutons à picto (lieu fixe, train, avion, bateau), nom, dates, positions sur carte (même sélecteur que pour les galeries : toucher la carte, faire glisser le point, « Me positionner »). Un lieu fixe n'a qu'une carte ; un trajet en a deux (départ et arrivée), la seconde se centrant sur le départ. Libellés adaptés (« Arrivée le / Départ le » pour un lieu, « Date de départ / d'arrivée » pour un trajet). Les cartes s'ouvrent près de la dernière étape.
- **Dates** : la date de fin suit la date de début tant qu'on n'y a pas touché (trajet d'une journée, séjour d'une nuit), et ne peut pas la précéder. Le remplissage du formulaire à l'ouverture d'une étape existante ne modifie jamais ses dates.
- **Changement de type** : passer un trajet en lieu fixe affiche, avant l'enregistrement, que sa position d'arrivée (et son tracé GPX) seront supprimés.
- **Pictos partagés** : `frontend/src/stageKinds.js` et le composant `StageIcon` définissent un picto par type ; la carte (6c) et le fil des photos (6d) les réutiliseront pour que chaque type ait partout le même.
- **À suivre (6b-2)** : envoi, aperçu et retrait du tracé GPX d'un trajet, depuis ce formulaire.

### Décisions d'implémentation (phase 6b-2 : tracé GPX dans le formulaire)

- **Section « Tracé GPX (facultatif) »** dans le formulaire d'un trajet déjà enregistré : choix du fichier (`.gpx`), progression réelle (« Envoi 50 % », puis « Lecture du fichier sur le serveur… » pendant que le serveur le lit et l'allège), remplacement par un nouveau fichier, retrait avec confirmation (le fichier d'origine n'est pas conservé : il faudra le renvoyer pour retrouver le tracé).
- **Reprise des extrémités** : case « Reprendre les extrémités du tracé comme positions de départ et d'arrivée », **décochée par défaut** (les positions étant obligatoires dès la création, les écraser doit être un choix délibéré). Cochée, les deux cartes et le formulaire suivent aussitôt les extrémités du GPX.
- **Quand la section n'apparaît pas** : à la création (il faut d'abord enregistrer l'étape), pour un lieu fixe, et pour un lieu fixe qu'on est en train de transformer en trajet sans l'avoir enregistré (un message le dit : le serveur refuserait le GPX). Elle disparaît aussi quand on passe un trajet en lieu fixe, avec l'avertissement de suppression du tracé.
- **Erreurs** : le message du serveur est affiché tel quel (fichier illisible, trop volumineux, ne contenant aucun tracé) ; une coupure réseau donne un message explicite ; le tracé précédent reste en place.
- **Aperçu** : les deux cartes de positionnement dessinent le tracé dans la couleur `--itinerary` du thème (variable ajoutée aux deux fichiers de thème, qui restent complets), cadrées sur le tracé et les marqueurs, et se mettent à jour à l'envoi, au remplacement et au retrait.


## 22. Fil des étapes (6d)

- **Fil mélangé** : `FeedService` fusionne les albums et les étapes dont la date de début est ≤ aujourd'hui (date du serveur). Ordre : date décroissante ; à date égale les albums d'abord (ordre habituel), puis les étapes, la plus avancée dans l'itinéraire en premier. La pagination (`pageSize`, 10 par défaut) compte albums et étapes ensemble ; `page` de `/api/public/map` suit le même calcul.
- **Étapes à venir** : hors fil et hors pagination. Le front les liste, de la plus lointaine à la plus proche, dans un bloc repliable « Voir la suite du voyage » en haut de la première page. Exception : une étape qui commence dans `app.upcoming-days` jours ou moins (défaut 7, 0 = aucune ; renvoyé par `/feed/positions`) est affichée, estompée, en tête de la ligne du temps, hors du bloc replié. Les étapes utilisent la même carte que les albums ; la carte les affiche estompées.
- **Cohérence « à venir »** : le serveur renvoie sa date du jour (`/feed/positions`) ; le front l'utilise pour la carte, le fil et le bloc.
- **Sélection** : `/etape/:id` (étape du fil : sa page est chargée et elle est mise en valeur ; étape à venir : première page, bloc ouvert). Un clic sur une étape du fil ne recharge pas la liste. Étape inconnue : retour à l'accueil. Albums et étapes ne sont jamais sélectionnés ensemble (une seule adresse).
- **Albums** : l'appareil photo de l'épingle figure aussi dans l'en-tête de chaque album du fil.
- **Fil de suivi** : une ligne verticale passe sous les cartes (albums et étapes, toutes de même largeur) et ne se voit qu'entre elles, (pas de pastille) ; ligne pointillée et carte opaque à bordure pointillée pour les étapes à venir (pas d'opacité : la ligne doit rester dessous) (bloc déplié et étapes imminentes font partie de la même ligne, au-dessus des albums).


## 23. Croisières (suivi AIS)

- **Parallèle du suivi d'un vol** : `TrackedTrip` de type `BOAT` (identifiant = MMSI, 9 chiffres), points de source `AIS`. API d'administration `/api/admin/cruises` (liste, création, `start`, `stop`, suppression), mêmes règles que `/api/admin/flights` : une seule croisière active à la fois (indépendante d'un vol actif), suppression refusée tant que le suivi est actif, suppression de la croisière et de ses points.
- **Batch** : `AisBatch` (un cycle toutes les `app.ais-poll-interval`, 5 minutes par défaut ; reprise après redémarrage ; un cycle en échec n'arrête pas le suivi ; position identique à la précédente non réenregistrée). Le planificateur a deux fils (`trackingScheduler`) : un vol et une croisière se suivent en même temps.
- **Fournisseur** : interface `VesselPositionProvider.findPosition(mmsi) → Optional<AisPosition>`, implémentée par `VesselClient`. L'appel réel n'est **pas écrit** (`fetch`, `TODO(AIS)`) ; `select` applique déjà les règles : MMSI exact, latitude/longitude présentes et valides (91° / 181° = « non disponible » en AIS), position de moins de 6 h, la plus récente si plusieurs.
- **Administration** : page *Croisières* (`/admin/croisieres`), copie de la page *Vols* avec le MMSI à la place de l'indicatif ; tracé en tirets sur la carte (déjà prévu pour la source `AIS`).

## 24. Montre Garmin (LiveTrack, phase 7e)

- **Principe** : suivre une activité Garmin via le lien « à partager avec ses amis » (`https://livetrack.garmin.com/session/{uuid}/token/{jeton}`). **Il n'existe pas d'API officielle** : le client reproduit ce que fait la page publique et peut cesser de fonctionner si Garmin la modifie (le reste du site n'est pas affecté).
- **Enchaînement (validé en conditions réelles)** : `GET` de la page (cookies dont `livetrack_csrf` + jeton CSRF dans une balise `<meta … csrf …>`), puis `GET /api/sessions/{id}/track-points/common?token=…&begin=<heure ISO>` avec les cookies et l'en-tête `livetrack-csrf-token`. L'API ne renvoie que les points postérieurs à `begin` (un point toutes les ~10 s) : on demande à chaque cycle depuis le dernier point connu, jamais la page entière (qui grossit sans cesse). Sur un 403 (cookie ou jeton expiré), le client recharge la page et réessaie **une fois**.
- **Données** : `TrackedTrip` de type `LIVETRACK` (identifiant = id de session, jeton dans la colonne `access_token`), points de source `GARMIN` (migration `V4__livetrack.sql`). Point reçu = latitude, longitude, heure `dateTime`.
- **Batch** : `LiveTrackBatch`, un cycle toutes les `app.livetrack-poll-interval` (60 s par défaut ; reprise après redémarrage). Premier cycle : points des 24 dernières heures. Événement `END` ou session disparue (HTTP 404/410) : suivi passé à « arrêté » automatiquement. Un cycle en échec n'arrête pas le suivi. Les doublons sont écartés par la base (même source, heure, position).
- **Administration** : `/api/admin/livetracks` (liste, création à partir du lien collé, `start`, `stop`, suppression), page *Montre Garmin* (`/admin/livetrack`). Une session suivie à la fois (indépendamment d'un vol ou d'une croisière) ; chaque activité crée un nouveau lien. Suppression refusée tant que le suivi est actif.
- **Lien court** : l'application Garmin propose un lien court (`https://gar.mn/xxxx`, HTTP 301 vers le lien long). À l'enregistrement, un lien long est enregistré tel quel ; un lien court est résolu par `GarminShortLinkResolver` (redirections suivies à la main, 3 étapes au plus, chacune en HTTPS sur `gar.mn` ou directement un lien LiveTrack valide ; toute autre destination est refusée sans être contactée), et seul le lien long est conservé. Lien inconnu : 400 ; Garmin injoignable : 502.
- **Sécurité** : le lien court contient un code propre à l'activité, traité comme secret au même titre que le jeton. Le jeton du lien donne la position en direct. Seuls l'identifiant et le jeton, validés par expression régulière stricte (hôte `livetrack.garmin.com` en HTTPS), servent à construire les appels : le serveur n'appelle jamais une adresse saisie. Le jeton n'est jamais renvoyé par l'API, ni journalisé ; les erreurs réseau sont converties sans adresse ni cause.
- **Carte** : tracé en tiret-point, couleur `--track-garmin` (présente dans tous les thèmes), marqueur de dernière position = point rond ; « en retard » après `app.track-late-after.garmin` (5 min par défaut).
- **À vérifier avec une vraie session** : comportement de `begin` très ancien (premier cycle), réponse exacte quand rien de nouveau, codes HTTP d'une session expirée, réaction de Cloudflare au `User-Agent` du serveur.

## 25. Prises de vue des photos (EXIF, phase 7f)

- **Données** : focale (mm), ouverture (f/), ISO, vitesse d'obturation (secondes) et date de prise de vue (`takenAt`), lues dans l'EXIF de l'**original** avec `metadata-extractor` 2.21.0 (la miniature et l'image pleine page n'ont plus de métadonnées, GPS compris).
- **Quand** : à l'envoi de la photo, juste après la création des miniatures (`MediaService.upload` → `ExifService.extractJson`). Photos seulement : les vidéos et les textes n'en ont pas.
- **Stockage** : colonne `gallery_item.exif_json` (migration `V5__photo_exif.sql`, `VARCHAR(300)` avec contrôle `JSON_VALID`), JSON prêt à l'emploi, par exemple `{"focalLength":24,"aperture":1.8,"iso":100,"shutter":"1/250","takenAt":"2026-10-12T16:03:21"}`. Chaque clé manque si la valeur est absente ou absurde ; `NULL` si aucune des cinq n'existe.
- **Date de prise de vue** : `DateTimeOriginal` (à défaut `DateTimeDigitized`), lue comme texte. L'EXIF standard ne contient **aucun fuseau horaire** : c'est l'heure affichée par l'horloge de l'appareil, que l'on suppose réglé sur l'heure locale du lieu. (L'EXIF 2.31 a ajouté les étiquettes facultatives `OffsetTimeOriginal`, etc. ; elles sont ignorées.) `takenAt` est donc une date-heure **locale sans fuseau** (`AAAA-MM-JJThh:mm:ss`), jamais convertie, ni côté serveur ni côté navigateur. Valeurs ignorées : `0000:00:00…`, dates invalides, années hors 1990–2100. Elle ne sert **ni à dater ni à trier** les photos. Pas de tri ni de filtre sur ces données, donc pas de colonnes séparées.
- **API** : `ItemDto.exif` insère le JSON stocké tel quel (`@JsonRawValue`) ; `null` pour les vidéos, les textes et les photos sans EXIF.
- **Robustesse** : un EXIF illisible ou absent n'empêche jamais l'envoi (résultat vide, message de débogage seulement).
- **Affichage** : sous le titre, dans la barre du viewer plein écran (`12 oct. 2026, 16:03 · 24 mm · f/1.8 · ISO 200 · 1/250 s`) ; une photo prise à 16 h au Japon s'affiche 16:00 quel que soit le fuseau du lecteur, uniquement pour une photo qui a des données.
- **Photos déjà envoyées** : elles n'ont pas de prises de vue (l'EXIF n'est lu qu'à l'envoi). Les renvoyer pour en profiter (pas de rattrapage prévu).

## 26. Page d'une galerie : contenu d'abord (phase 7g)

- **Problème** : sur téléphone, l'envoi de photos (bloc *Contenu*) était sous le formulaire de réglages : défilement obligatoire et risque de modifier la fiche par erreur.
- **Décision** : pour une galerie existante, le bloc *Contenu* passe **en premier** et les réglages (titre, lieu, date, position) sont **repliés** dans un `<details>` « Réglages de la galerie » sous le contenu. Pour une nouvelle galerie, le formulaire est ouvert et il n'y a pas encore de contenu.
- **Titre** : le bloc s'intitule « Contenu · {titre de la galerie} » (titre enregistré, mis à jour après chaque enregistrement des réglages) pour toujours savoir dans quelle galerie on envoie.
- **Carte** : la carte de position n'est créée qu'à l'ouverture des réglages (Leaflet ne se dimensionne pas dans un bloc replié).

## 27. Infobulle des galeries (phase 7h)

- L'infobulle d'une galerie reprend la mise en forme de celle d'une étape (`pin-tip--stage` : largeur adaptée au contenu, 11 rem au moins, 20 rem au plus, mêmes marges) : miniature à gauche, puis une ligne par information : titre, date, lieu. Elle peut s'élargir à 22 rem pour la miniature.

## 28. Page Config (phase 7i)

- **Rôle** : page d'administration **en lecture seule** (`/admin/config`, tuile « Config » du menu ; pas dans la barre du bas, déjà chargée) alimentée par `GET /api/admin/info` (`InfoService`). Les réglages restent dans `application.yml`.
- **Contenu** : *Version* (numéro et date de construction, via `build-info` de Maven ; « développement » sans), *Suivi en direct* (par source : état du suivi en cours, délai de relève, seuil « en retard », nombre de positions, dernière position), *Contenu* (galeries, photos, vidéos, textes, taille cumulée des originaux, espace disque libre), *Limites* (tailles maximales, galeries par page, étape « bientôt », coupure du tracé, précision GPS), *Outils* (ffmpeg et sa version, URL de l'API de positions pour GPSLogger, nom de l'en-tête du token).
- **Secrets** : mots de passe, token d'API et jetons LiveTrack ne sont jamais renvoyés (le token n'est que nommé : valeur dans le fichier de configuration). L'URL de l'API de positions est complétée côté navigateur avec l'adresse du site.
- **Délais de relève** : lus sur les batchs eux-mêmes (`interval()`), donc toujours ceux réellement appliqués.
- **Page Points** (liste et correction des points GPS) : reportée après un moment d'utilisation sur le terrain.

## 29. Envois fiabilisés (phase 8a)

- **Problème** : un envoi d'un seul tenant (une requête pour tout le fichier) repart de zéro à chaque coupure (4G/5G, wifi d'hôtel), et une réponse perdue après un envoi réussi donnait un doublon au réessai.
- **Protocole par morceaux, avec reprise** (`UploadService`, `UploadController`, état dans `<médias>/.uploads`) :
  1. `POST /api/admin/galleries/{id}/uploads` `{filename, size, fingerprint}` → `{id, received, size, chunkSize}`. L'empreinte (nom, taille, date de modification, hachée côté serveur) fait **reprendre l'envoi interrompu du même fichier**, même après un rechargement de la page.
  2. `PUT /api/admin/uploads/{id}?offset=N` (octets bruts, 8 Mo au plus) : le morceau doit commencer **exactement** à l'octet reçu ; sinon `409 {received}`. Un morceau coupé en route est retiré (le fichier ne contient jamais de morceau partiel) ; réponse 408 si le corps est incomplet.
  3. `POST /api/admin/uploads/{id}/complete` : traitement habituel (miniatures, EXIF, base), le fichier est **déplacé**, pas copié (`MediaService.ingest`). **Idempotent** : marque `<id>.done` ; redemandé, il renvoie le même élément. Fichier refusé : l'envoi est effacé ; échec avant déplacement (disque...) : l'envoi est conservé pour réessayer.
  4. `GET /api/admin/uploads/{id}` (état) et `DELETE` (abandon).
- **Contrôles précoces** : dès le premier morceau, le type est reconnu sur le contenu (415 sinon) et la taille comparée à la limite de son type (413) ; l'annonce de taille est bornée à la plus grande limite dès le début.
- **Client** (`resumableUpload.js`) : morceaux de 2 Mo, divisés par deux à chaque échec (plancher 256 Ko), doublés après trois succès (plafond 4 Mo) ; après toute erreur, état redemandé au serveur (le morceau a pu arriver sans réponse) ; réessais à 1, 2, 4, 8, 15, 30 s, dix échecs de suite au plus ; blocage sans octet pendant 45 s = échec réessayé ; réponse 200 sans JSON (portail captif) = échec réessayé ; 5xx, 408, 429 réessayés, les autres erreurs HTTP (401, 413, 415...) arrêtent l'envoi ; **hors connexion**, attente de l'événement `online` sans compter d'échec ; serveur ayant oublié l'envoi (404) : recommencé de zéro, deux fois au plus. À l'arrêt définitif, l'entrée passe en erreur « réseau » : elle reprend toute seule au retour de la connexion, ou par « Réessayer ».
- **Nettoyage** : les envois (et marques) inactifs depuis plus de 48 h sont effacés au début de chaque nouvel envoi. Annuler dans l'interface interrompt l'envoi mais garde ce qui est reçu 48 h (une reprise reste possible).
- **Sécurité** : l'identifiant d'envoi est un UUID validé (aucun chemin), `.uploads` est refusé par nginx (`/media/.uploads/` → 404), le type reste reconnu sur le contenu.
- **Conservé** : l'envoi en un bloc `POST .../items/media` (scripts, curl).

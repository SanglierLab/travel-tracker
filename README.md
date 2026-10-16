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

Depuis l'envoi par morceaux (voir « Envoyer des photos et vidéos en voyage »), chaque requête d'envoi est courte (2 à 4 Mo) : ces délais longs ne servent
plus qu'à la validation d'une grosse vidéo (traitement sur le serveur, quelques secondes à quelques minutes sur le NAS) et aux envois en un seul bloc par `curl`.

### Envoyer des photos et vidéos en voyage

Dans la page d'une galerie, les fichiers partent un par un, **en petits morceaux** (2 Mo, réduits jusqu'à 256 Ko si la connexion est mauvaise, agrandis jusqu'à 4 Mo
quand elle tient). Si la connexion coupe, se bloque ou renvoie la page d'un portail de wifi, le morceau est réessayé automatiquement (1, 2, 4, 8, 15 puis 30 s, dix échecs
de suite au plus). Hors connexion, l'envoi attend le retour du réseau. Si l'envoi finit par s'arrêter (« Connexion trop instable »), ce qui est déjà arrivé est conservé
sur le serveur pendant 48 h : « Réessayer », ou simplement rechoisir le même fichier (même après avoir rechargé la page), reprend là où il s'était arrêté.
Une réponse perdue ne crée jamais de doublon. Le type et la taille du fichier sont contrôlés dès le premier morceau, pas après 500 Mo d'envoi.

Pour un script : `POST /api/admin/galleries/{id}/items/media` (fichier entier, champ `file`) reste disponible.

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
curl -s $BASE/api/public/feed?page=1          # le fil : albums et étapes commencées mélangés
curl -s $BASE/api/public/feed/positions       # date du jour du serveur + page de chaque étape
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
| **HTTP Body** | `{"latitude":%LAT,"longitude":%LON,"time":"%TIME","accuracy":"%ACC"}` |
| **HTTP Headers** | `Content-Type: application/json` puis, à la ligne : `X-API-Token: <ton token>` |
| **HTTP Method** | `POST` |
| **Basic Authentication** | laisser vide |

Le token est celui de `app.api-token` dans `application.yml`. Il passe dans un en-tête, jamais dans l'URL.

**Si l'heure est refusée** (message « Date illisible » dans les journaux de GPSLogger), remplacer le corps par
`{"latitude":%LAT,"longitude":%LON,"timestamp":%TIMESTAMP,"accuracy":"%ACC"}` : l'API accepte aussi un horodatage en secondes.

`%ACC` (précision en mètres) est **entre guillemets** exprès : GPSLogger peut envoyer une valeur vide, qui ferait un JSON invalide sans les guillemets.
Le serveur lit « vide » ou « 0 » comme « précision inconnue » (le point est alors accepté).

**Éviter les points aberrants** (un point à des dizaines de kilomètres de la réalité, puis retour à la normale : typique d'une localisation
« réseau » ou périmée). Dans les réglages de GPSLogger (les libellés varient selon la version) :

- ne garder que la source **GPS/GNSS** : désactiver la localisation « réseau » (antennes) et « passive » (autres applications) ;
- activer le **filtre de précision** (environ 50 m) et laisser GPSLogger chercher environ 30 s un point qui le respecte ;
- autoriser la localisation « tout le temps » pour l'application et la dispenser de l'optimisation de batterie ;
- intervalle de 60 à 300 secondes et filtre de distance de 50 m, pour économiser batterie et données.

Le serveur applique en plus ses propres règles à l'ajout : un point dont la précision annoncée est pire que `app.track-max-accuracy-meters`
(100 m par défaut) ou dont la position est 0°/0° (GPS sans position) est **écarté et non enregistré**. La réponse reste un code 200,
`{"status":"ignored","reason":"accuracy"}` ou `"no-fix"`, pour que GPSLogger ne réessaie pas un point volontairement écarté.
Un point sans précision connue est accepté.

### Tester sans téléphone

```bash
BASE=https://voyage.sanglierlab.fr    # ou http://localhost:8080 en développement
TOKEN='<ton token>'

# Premier envoi : {"status":"created"}
curl -i -X POST $BASE/api/track/points -H "Content-Type: application/json" -H "X-API-Token: $TOKEN" \
  -d '{"latitude":48.8583,"longitude":2.2945,"time":"2026-10-04T10:00:00.000Z","accuracy":"12.5"}'
# Même envoi : {"status":"duplicate"} (code 200 aussi : le point existe déjà, rien n'est ajouté)
# Précision trop mauvaise ("accuracy":"250") : 200 {"status":"ignored","reason":"accuracy"}, rien n'est enregistré
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
`app.track-gap-hours` sans envoyer de position) et la dernière position connue (`last`). La carte publique l'affiche : trait plein pour le téléphone,
pointillés pour un avion, tirets espacés pour un bateau (couleurs `--track-*` du thème), avec une légende. Les points aberrants du téléphone (un point isolé à des dizaines de kilomètres, puis retour à la normale) sont masqués à l'affichage, sans rien supprimer en base (voir SPECIFICATIONS §18).

La **dernière position** a un marqueur selon sa source : gros point rond pour le téléphone, picto de bateau (AIS) ou d'avion (ADS-B) seul, de la couleur
du tracé. L'avion est orienté selon son cap, déduit du dernier point du tracé de la même source assez éloigné de la dernière position (pas de cap
avec un seul point : l'avion pointe alors vers le nord). Quand aucune position n'est arrivée depuis un certain délai, le marqueur passe en style
« en retard » (gris `--track-late`, estompé, infobulle « En retard ») ; le délai se règle par source dans `application.yml` :

```yaml
app:
  track-late-after:
    device: 10m   # défauts : 10m, 5m, 20m
    adsb: 5m
    ais: 20m
```


## Suivi d'un vol (ADS-B)

Chaque vol se déclare dans l'administration (numéro de vol et date), puis son suivi se lance et s'arrête **à la main** (bouton on/off).
Tant que le suivi est actif, un batch interroge le fournisseur ADS-B toutes les 30 secondes (`app.adsb-poll-interval`) et enregistre les
positions en base (source `ADSB`, rattachées au vol) : elles s'affichent en pointillés sur la carte publique. Un seul vol peut être suivi
à la fois, et le suivi d'un vol actif reprend tout seul après un redémarrage du serveur.

**Dans l'administration** : *Menu → Vols* (ou « Vols » dans la barre de navigation). On y enregistre le numéro de vol et la date (heure UTC facultative),
puis le bouton **on/off** de chaque vol lance ou arrête le suivi. La liste montre l'état de chaque vol, le nombre de positions enregistrées et l'heure de la
dernière (elle se met à jour toute seule toutes les 15 secondes tant qu'un suivi est actif). Démarrer un deuxième vol est refusé tant que le premier est suivi.

**Fournisseur : [adsb.lol](https://api.adsb.lol/docs)** (`GET /v2/callsign/{callsign}`, sans clé), via `flight/adsb/api/AdsbLolClient.java`
(interface `FlightPositionProvider`).

- **Saisir l'indicatif ICAO du vol (callsign), pas le numéro commercial** : `AFR186`, pas `AF186`. Les compagnies ont deux codes
  (Air France : `AF` pour les billets, `AFR` pour la radio) et seul le second est émis par l'avion. Le numéro de vol de la carte d'embarquement
  ne marchera donc pas tel quel.
- Aucune position (avion hors de portée des récepteurs : océan, Arctique ; pas encore parti ; déjà posé) n'est **pas** une erreur : le cycle est
  simplement sauté (`ADS-B AFR186 : aucune position disponible` dans les journaux) et le suivi continue.
- Seule une position de moins de 10 minutes est retenue. Si plusieurs appareils ont le même indicatif, le plus récemment vu gagne.
- Une position identique à la précédente n'est pas réenregistrée (un avion posé, ou un fix non renouvelé).
- Délais réseau : 5 s pour se connecter, 10 s pour lire la réponse. Une erreur (réseau, HTTP 5xx, 429...) est journalisée et le cycle suivant réessaie.
- L'API renvoie l'heure `now` en millisecondes et `seen_pos` en secondes avant `now` ; l'heure retenue est `now - seen_pos`, arrondie à la seconde.
- `app.adsb-poll-interval` (30 s par défaut) règle la fréquence ; adsb.lol est un service communautaire gratuit : ne pas descendre sous 10 s.

### Tester avec curl (session et CSRF comme dans « Tester l'API »)

```bash
# Enregistrer un vol (l'heure UTC est facultative) ; la réponse contient l'identifiant du vol
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -d '{"identifier":"AFR1234","date":"2026-10-12","time":"10:30"}' $BASE/api/admin/flights

curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X POST $BASE/api/admin/flights/1/start   # on
curl -s -b $JAR $BASE/api/admin/flights                                             # état, nombre de positions, dernière position
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X POST $BASE/api/admin/flights/1/stop    # off
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X DELETE $BASE/api/admin/flights/1       # supprime le vol ET ses positions
```

Les journaux du serveur montrent chaque cycle (« ADS-B AFR1234 : 1 position(s) reçue(s), 1 enregistrée(s) »), et
`SELECT * FROM track_point WHERE source = 'ADSB';` montre les points. Pour nettoyer les points fictifs d'un essai : supprimer le vol.


## Suivi d'une croisière (AIS)

Le parallèle exact du suivi d'un vol, pour les navires : une croisière se déclare dans l'administration (**MMSI** du navire et date de départ), puis son suivi
se lance et s'arrête à la main (bouton on/off). Tant que le suivi est actif, un batch interroge le fournisseur AIS toutes les 5 minutes
(`app.ais-poll-interval`) et enregistre les positions (source `AIS`, rattachées à la croisière) : elles s'affichent en tirets sur la carte publique.
Une seule croisière peut être suivie à la fois, indépendamment d'un vol (les deux suivis peuvent tourner ensemble), et le suivi reprend tout seul
après un redémarrage du serveur.

**Dans l'administration** : *Menu → Croisières*. Le MMSI est le numéro à 9 chiffres du navire (visible sur les sites de suivi maritime), par exemple `227123456`.

**État actuel : le fournisseur de données n'est pas encore branché.** Le batch tourne, mais `VesselClient` ne fait encore aucun appel : aucune position n'est
récupérée et les journaux l'indiquent une fois (`AIS : fournisseur de données pas encore branché`). Pour le brancher, un seul endroit :
`backend/src/main/java/fr/sanglierlab/traveltracker/cruise/ais/api/VesselClient.java`, méthode `fetch(String mmsi)`, repérée par un `TODO(AIS)` (avec un exemple)
et la constante `BASE_URL`. Le reste est déjà en place : délais réseau (5 s / 10 s), normalisation du MMSI, choix de la position (MMSI exact, latitude/longitude
valides, valeurs « non disponible » de l'AIS 91° / 181° écartées, position de moins de 6 h, la plus récente si plusieurs). Il suffit de convertir la réponse du
fournisseur en `Vessel(mmsi, latitude, longitude, heure UTC)`. Une clé d'API se lit dans `application.yml` (via `AppProperties`), jamais dans le code.

```bash
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -d '{"identifier":"227123456","date":"2026-10-20","time":null}' $BASE/api/admin/cruises
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X POST $BASE/api/admin/cruises/1/start   # on ; /stop pour arrêter
curl -s -b $JAR $BASE/api/admin/cruises                                             # état, nombre de positions, dernière position
```

## Suivi d'une montre Garmin (LiveTrack)

Même principe que les vols et les croisières, avec le lien de partage LiveTrack d'une montre Garmin. Sur la montre, démarrez une activité avec LiveTrack
et envoyez-vous le lien de partage. Dans l'administration (*Menu → Montre Garmin*), collez le lien (court `https://gar.mn/…`, converti automatiquement en lien long, ou long `https://livetrack.garmin.com/session/…`), enregistrez la session, puis activez
son suivi. Un batch lit les nouvelles positions toutes les 60 secondes (`app.livetrack-poll-interval`) et les affiche en tiret-point sur la carte (source `GARMIN`).
Le suivi s'arrête tout seul quand l'activité se termine. Chaque activité crée un nouveau lien, à enregistrer à chaque fois ; une seule session est suivie à la fois.

**Attention** : Garmin ne fournit pas d'API officielle. Ce suivi reproduit ce que fait la page publique du lien et peut cesser de fonctionner si Garmin la
modifie. Le lien contient un jeton secret (il donne la position en direct) : ne le publiez pas. Il est stocké en base, jamais renvoyé par l'API ni écrit dans
les journaux. L'outil manuel `GarminLiveTrackManual` (dossier de tests) lit un `garmin-curl.txt` local, déjà exclu de git.

```bash
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -d '{"link":"https://livetrack.garmin.com/session/…/token/…","name":"Randonnée"}' $BASE/api/admin/livetracks
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X POST $BASE/api/admin/livetracks/1/start   # on ; /stop pour arrêter
```

## Étapes du voyage (API)

Les étapes sont les grandes lignes du voyage : un **lieu fixe** (Tokyo, du 1er au 5 mars) ou un **trajet** en train, avion ou bateau
(Tokyo → Shimoda, le 5 mars). Elles s'affichent sur la carte, même dans le futur, et dans le fil des photos (étape 6d à venir).

**Sur la carte publique (6c-1)** : chaque étape est un badge arrondi avec le picto de son type (lieu, train, avion, bateau), placé au milieu du trajet pour un trajet ; une étape dont la date de début n'est pas atteinte est estompée (« à venir »). Les albums gardent leur épingle, avec un appareil photo au centre. La légende liste ces éléments. 

**Itinéraire (6c-2)** : une ligne épaisse fléchée, relie les étapes dans l'ordre choisi par l'admin. Elle suit le fichier GPX
s'il y en a un ; sinon c'est un **arc de cercle** entre le départ et l'arrivée (porté par le grand cercle pour un avion), toujours courbé sur la droite du sens de marche : un aller et son retour se courbent de part et d'autre et ne se superposent pas. Une liaison pointillée fine relie deux étapes consécutives distantes de plus d'1 km.
La ligne passe sous le tracé GPS. Cliquer sur une étape (badge ou ligne) la sélectionne : adresse `/etape/:id`, carte cadrée dessus, badge agrandi.


**Fil des photos (6d)** : les étapes déjà commencées s'intercalent entre les albums, par date de début (sous les albums du même jour) ; une étape est une pastille
arrondie avec le picto de son type. Albums et étapes sont paginés ensemble (10 par page). Les étapes à venir ne sont pas dans le fil : un bloc repliable
« Voir la suite du voyage » les liste en haut de la première page, de la plus lointaine à la plus proche (comme le reste du fil). Une étape qui commence dans `upcoming-days` jours ou moins (7 par défaut, réglable dans `application.yml`, 0 pour désactiver) n'est pas repliée : elle est affichée estompée en tête du fil. Une ligne verticale (le « fil de suivi ») passe sous les cartes, qui ont toutes la même largeur . Pour les étapes à venir, la ligne est en pointillés et la carte, opaque, en bordure pointillée sur le fond de la page. Les étapes ont la même carte que les albums (même date, même titre, même sélection) ; seul le picto de leur type les distingue. Cliquer une étape du fil la met en valeur dans le fil et sur la carte (et inversement) ;
l'en-tête d'un album porte le même appareil photo que son épingle. « À venir » se décide avec la **date du jour du serveur** (fuseau de la machine :
pensez à `TZ=Europe/Paris` dans le conteneur) ; le navigateur reprend cette date pour que carte et fil soient toujours d'accord.

**Dans l'administration** : *Menu → Étapes*. On y ajoute, supprime, réordonne (monter/descendre, « Trier par date ») et positionne les étapes sur la carte.
Pour un trajet, le formulaire permet d'envoyer un fichier **GPX** : il est allégé à l'envoi et son tracé s'affiche sur les cartes de positionnement ;
la case « Reprendre les extrémités du tracé comme positions » place le départ et l'arrivée toute seule.

Les commandes `curl` ci-dessous permettent aussi de tout tester directement (session et CSRF comme dans « Tester l'API »).

```bash
# Un lieu fixe : une seule position (début), pas de GPX. Types : PLACE, TRAIN, PLANE, BOAT
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" $BASE/api/admin/stages \
  -d '{"name":"Tokyo","kind":"PLACE","startDate":"2026-03-01","endDate":"2026-03-05","startLatitude":35.6762,"startLongitude":139.6503}'

# Un trajet : position de début ET de fin
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" $BASE/api/admin/stages \
  -d '{"name":"Train Tokyo → Shimoda","kind":"TRAIN","startDate":"2026-03-05","endDate":"2026-03-05","startLatitude":35.6812,"startLongitude":139.7671,"endLatitude":34.6667,"endLongitude":138.9453}'

# Tracé GPX d'un trajet (10 Mo maximum). useEndpoints=true reprend les extrémités du GPX comme positions de début et de fin
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -F file=@trajet.gpx "$BASE/api/admin/stages/2/gpx?useEndpoints=true"
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X DELETE $BASE/api/admin/stages/2/gpx      # retire le tracé

# Ordre (celui du tracé fléché) : monter / descendre, ou tout recaler sur les dates
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X POST "$BASE/api/admin/stages/2/move?direction=UP"
curl -s -b $JAR -H "X-XSRF-TOKEN: $XSRF" -X POST $BASE/api/admin/stages/sort-by-date

# Lecture publique : toutes les étapes dans l'ordre, avec leur tracé GPX allégé
curl -s $BASE/api/public/itinerary
```

La migration `V3__stage.sql` crée la table au prochain démarrage du serveur.
Le GPX est lu sur le serveur (sans dépendance, protégé contre les fichiers XML piégés) puis **allégé** (environ 1 500 points au plus) : seul ce tracé léger est conservé, pas le fichier d'origine.

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

# Décisions d'architecture

## D1 — Code en anglais, interface en français
Le code média réutilisé est en anglais. Le franciser introduirait des erreurs
de portage sans bénéfice. Seuls les libellés visibles sont en français, ainsi
que les messages d'erreur de l'API (affichés tels quels par le frontend).

## D2 — Une seule table `element` pour photos, vidéos et textes
Un champ `position` unique ordonne le flux complet.

## D3 — Upload en flux, pas en `byte[]`
`file-size-threshold: 1MB` + `transferTo(tmp)`. RAM constante quelle que soit
la taille du fichier. Volume `./tmp` monté : sur certains Synology `/tmp` est
en RAM, ce qui annulerait le bénéfice.

## D4 — Pas de déduplication par hash
Un seul contributeur. Le SHA-256 impose de lire intégralement le fichier.

## D5 — Pas de modération
Remplacée par un booléen `published` sur `gallery`.

## D6 — GPS EXIF conservé
`exif_latitude` / `exif_longitude` alimentent une suggestion de position.

## D7 — Idempotence de l'ingestion
Clé unique `(source, measured_at, trip_id)`.

## D8 — CSRF désactivé, cookie `SameSite=Strict`
Aucun formulaire HTML classique : le frontend n'émet que du JSON via `fetch`.
Un cookie `SameSite=Strict` n'est jamais transmis lors d'une requête initiée
par un site tiers, ce qui neutralise le CSRF sans imposer la gestion d'un
jeton supplémentaire côté Vue.
Conditions à préserver : ne jamais accepter `application/x-www-form-urlencoded`
sur une route mutante, et conserver `same-site: strict`.

## D9 — Deux chaînes de sécurité distinctes
`/api/ingest/**` : jeton porteur, sans état, sans session.
Le reste : session HTTP pour l'admin, lecture publique libre.
Séparer évite qu'un tracker mobile ne crée des sessions à chaque point émis.

## D10 — Session de 30 jours
Le voyageur administre depuis son téléphone pendant plusieurs semaines.
Une expiration courte l'obligerait à se reconnecter sans cesse, souvent sur
un réseau instable. Le cookie est `HttpOnly`, `Secure` et `SameSite=Strict`.

## D11 — HEIC abandonné
Prises de vue Android et Nikon hybride transférées via le téléphone : JPEG.
`HeicService`, `libheif-examples` et la branche de conversion sont supprimés.

## D12 — Garde-fous au démarrage
`StartupInitializer` refuse de démarrer en profil `prod` si le jeton
d'ingestion ou le compte administrateur sont restés à leur valeur par défaut.
Mieux vaut un échec au déploiement qu'une porte ouverte pendant six mois.

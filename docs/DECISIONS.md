# Décisions d'architecture

## D1 — Code en anglais, interface en français
Le code média réutilisé (wedding 1.x) est en anglais. Le franciser introduirait
des erreurs de portage sans bénéfice. Seuls les libellés visibles sont en français.

## D2 — Une seule table `element` pour photos, vidéos et textes
Un champ `position` unique ordonne le flux complet. Évite d'entrelacer deux
collections à l'affichage et rend le réordonnancement trivial.

## D3 — Upload en flux, pas en `byte[]`
`file-size-threshold: 1MB` + `MultipartFile.transferTo(tmp)`. Une vidéo de 800 Mo
n'occupe jamais la RAM du NAS. Volume `./tmp` monté pour éviter un `/tmp` en RAM.

## D4 — Pas de déduplication par hash
Un seul contributeur : le risque de doublon est marginal, et le coût (lecture
intégrale du fichier pour le SHA-256) est réel sur vidéo.
À réintroduire si besoin, via une colonne `hash_sha256` nullable.

## D5 — Pas de modération
`PENDING/APPROVED/REJECTED` disparaît. Un booléen `published` sur `gallery`
suffit à garder un brouillon hors ligne le temps de le compléter.

## D6 — GPS EXIF conservé
`exif_latitude` / `exif_longitude` alimentent une suggestion de position lors de
la création de la galerie, en complément du bouton « me géolocaliser ».

## D7 — Idempotence de l'ingestion
Clé unique `(source, measured_at, trip_id)`. Le tracker mobile peut réémettre
son tampon sans créer de doublons.

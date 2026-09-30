# Traceur mobile — protocole

## Envoi d'une position

```
POST /api/ingest/points
Authorization: Bearer <app.ingest-token>
Content-Type: application/json

{
  "measuredAt": "2026-10-10T12:34:00Z",
  "latitude": 35.686,
  "longitude": 139.753,
  "altitudeM": 12.0,
  "speedKmh": 4.3,
  "headingDeg": 180,
  "accuracyM": 8
}
```

Seules la latitude et la longitude sont obligatoires. La source vaut `DEVICE`
par défaut, l'horodatage est celui de la réception s'il est omis.

## Envoi groupé

Le même point d'entrée accepte un tableau :

```json
[ { "measuredAt": "…", "latitude": …, "longitude": … },
  { "measuredAt": "…", "latitude": …, "longitude": … } ]
```

## Comportement attendu du traceur

Accumuler les relevés localement et les transmettre par lots quand le réseau
le permet. En cas de doute sur la bonne réception d'un lot, le réémettre : les
positions déjà connues sont écartées sans erreur.

La réponse détaille le sort de chaque point :

```json
{ "received": 12, "stored": 9, "duplicates": 3, "rejected": 0 }
```

Le traceur peut alors vider son tampon dès lors que `stored + duplicates`
couvre ce qu'il a envoyé.

## Périodicité suggérée

Une position toutes les une à cinq minutes en déplacement suffit à un tracé
lisible. Plus fréquent alourdit la base sans gain visible à l'échelle d'une
carte de voyage.

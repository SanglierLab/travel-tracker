# Contrat d'API — Travel Tracker 2.0

Convention : routes et champs JSON en anglais, libellés d'interface en français.

## Public (sans authentification)
| Méthode | Route | Description |
|---|---|---|
| GET | `/api/galleries?page=0&size=5` | Liste anti-chronologique paginée |
| GET | `/api/galleries/markers` | Version allégée pour les marqueurs Leaflet |
| GET | `/api/galleries/{id}` | Détail + éléments ordonnés |
| GET | `/api/trace?source=&from=&to=` | Points de trace |
| GET | `/api/trips` | Vols / traversées configurés |
| GET | `/media/{size}/**` | `thumb` \| `medium` \| `original` |

## Admin (session HTTP, comptes du fichier de conf)
| Méthode | Route |
|---|---|
| POST | `/api/auth/login`, `/api/auth/logout`, GET `/api/auth/me` |
| POST PUT DELETE | `/api/admin/galleries[/{id}]` |
| POST | `/api/admin/galleries/{id}/media` (multipart, n fichiers) |
| POST | `/api/admin/galleries/{id}/text` (bloc markdown) |
| PUT DELETE | `/api/admin/elements/{id}` |
| PUT | `/api/admin/galleries/{id}/order` (liste d'ids ordonnée) |
| GET PUT DELETE | `/api/admin/trace[/{id}]` |
| GET POST PUT DELETE | `/api/admin/trips[/{id}]` |

## Ingestion temps réel (token)
`POST /api/ingest/points` — en-tête `Authorization: Bearer <token>`

Point unique **ou** tableau de points (rattrapage après coupure réseau) :
```json
[{ "source":"DEVICE", "measuredAt":"2026-10-10T12:34:00Z",
   "latitude":35.686, "longitude":139.753,
   "altitudeM":12.0, "speedKmh":4.3, "headingDeg":180, "accuracyM":8 }]
```
Réponses : `201 Created` (avec le nombre de points insérés / ignorés), `401`, `400`.
La contrainte `uk_trace_dedup` rend l'envoi **idempotent** : un renvoi du même
lot n'insère pas de doublon.

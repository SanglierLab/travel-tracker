# Contrat d'API — Travel Tracker 2.0

Routes et champs JSON en anglais, messages d'erreur en français.

## Authentification
| Méthode | Route | Accès | Description |
|---|---|---|---|
| POST | `/api/auth/login` | public | `{username, password}` → `{authenticated, username}` |
| POST | `/api/auth/logout` | session | invalide la session |
| GET | `/api/auth/me` | public | état courant, `authenticated:false` si visiteur |

## Public (sans authentification)
| Méthode | Route |
|---|---|
| GET | `/api/galleries?page=0&size=5` |
| GET | `/api/galleries/markers` |
| GET | `/api/galleries/{id}` |
| GET | `/api/trace?source=&from=&to=` |
| GET | `/api/trips` |
| GET | `/media/{size}/**` (`thumb` \| `medium` \| `original`) |

## Administration (session, rôle ADMIN)
| Méthode | Route |
|---|---|
| POST PUT DELETE | `/api/admin/galleries[/{id}]` |
| POST | `/api/admin/galleries/{id}/media` (multipart) |
| POST | `/api/admin/galleries/{id}/text` |
| PUT DELETE | `/api/admin/elements/{id}` |
| PUT | `/api/admin/galleries/{id}/order` |
| GET PUT DELETE | `/api/admin/trace[/{id}]` |
| GET POST PUT DELETE | `/api/admin/trips[/{id}]` |

## Ingestion temps réel (jeton)
`POST /api/ingest/points` — `Authorization: Bearer <app.ingest-token>`

Point unique ou tableau (rattrapage après coupure réseau) :
```json
[{ "source":"DEVICE", "measuredAt":"2026-10-10T12:34:00Z",
   "latitude":35.686, "longitude":139.753,
   "altitudeM":12.0, "speedKmh":4.3, "headingDeg":180, "accuracyM":8 }]
```

## Format d'erreur (uniforme)
```json
{
  "timestamp": "2026-10-10T12:34:00Z",
  "status": 400,
  "message": "Formulaire invalide",
  "path": "/api/auth/login",
  "errors": [ { "field": "username", "message": "L'identifiant est obligatoire" } ]
}
```

# Contrat d'API (v1)

Base : `/api`

## Public (aucune authentification)
| Méthode | Route | Description |
|---|---|---|
| GET | `/api/galeries?page=0&size=5` | Liste anti-chronologique paginée (résumés) |
| GET | `/api/galeries/carte` | Toutes les galeries en version légère (marqueurs) |
| GET | `/api/galeries/{id}` | Détail : entête + éléments ordonnés |
| GET | `/api/trace?depuis=&jusqu=&source=` | Points de trace (device / adsb / ais) |
| GET | `/api/trajets` | Trajets configurés (type, statut, libellé) |
| GET | `/media/{taille}/{fichier}` | `miniature` \| `plein` \| `original` |

## Admin (session, login/mdp du fichier de conf)
| Méthode | Route |
|---|---|
| POST | `/api/auth/login` / `/api/auth/logout` / GET `/api/auth/moi` |
| POST/PUT/DELETE | `/api/admin/galeries[/{id}]` |
| POST | `/api/admin/galeries/{id}/medias` (multipart, n fichiers) |
| POST | `/api/admin/galeries/{id}/textes` (bloc markdown) |
| PUT/DELETE | `/api/admin/elements/{id}` (légende, texte, suppression) |
| PUT | `/api/admin/galeries/{id}/ordre` (liste d'ids ordonnée) |
| GET/PUT/DELETE | `/api/admin/trace[/{id}]` (liste paginée, correction, suppression) |
| GET/POST/PUT/DELETE | `/api/admin/trajets[/{id}]` |

## Ingestion temps réel (token)
`POST /api/ingest/points` – en-tête `Authorization: Bearer <token>`
```json
{ "source":"DEVICE", "mesureLe":"2026-10-10T12:34:00Z",
  "latitude":35.686, "longitude":139.753,
  "altitudeM":12.0, "vitesseKmh":4.3, "capDeg":180, "precisionM":8 }
```
Accepte aussi un tableau de points (envoi groupé après perte de réseau).
Réponses : `201` / `401` / `400`.

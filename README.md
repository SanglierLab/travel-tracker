# Travel Tracker 2.0

Carnet de voyage auto-hébergé : galeries photo/vidéo géolocalisées + trace GPS temps réel.
Alternative DIY à Polarsteps, à usage familial.

## Monorepo
- `backend/`  : Spring Boot 3 + MariaDB + stockage médias local
- `frontend/` : Vue 3 + Vite + Leaflet
- `docs/`     : contrat d'API, décisions d'architecture
- `media/`    : volume des médias (hors Git)

## Conventions
- **Code, base de données et API : anglais** (cohérent avec le code média réutilisé).
- **Interface utilisateur : français uniquement.**

## Démarrage dev
```bash
# 1. base
docker compose up -d db
# 2. backend
cd backend && ./mvnw spring-boot:run
# 3. frontend
cd frontend && npm install && npm run dev
```

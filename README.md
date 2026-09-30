# Carnet de voyage (alternative DIY à Polarsteps)

Monorepo : `backend/` (Spring Boot + MariaDB) et `frontend/` (Vue 3 + Vite + Leaflet).
Déploiement : 2 conteneurs Docker sur NAS Synology, derrière HAProxy.

## Arborescence
- `backend/`  : API REST, stockage médias, ingestion des positions temps réel
- `frontend/` : SPA publique + interface d'administration
- `docker-compose.yml` : orchestration locale / NAS
- `media/`    : volume des médias (monté dans le conteneur backend)

## Démarrage dev
1. MariaDB local (ou conteneur) + base `carnet`
2. `cd backend && ./mvnw spring-boot:run` (profil dev)
3. `cd frontend && npm install && npm run dev` (proxy Vite -> :8080)

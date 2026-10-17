-- Archivage des trajets suivis (vols, croisières, sessions LiveTrack) : le trajet quitte la liste courante
-- mais ses positions restent en base (et sur la carte).
ALTER TABLE tracked_trip ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE AFTER status;

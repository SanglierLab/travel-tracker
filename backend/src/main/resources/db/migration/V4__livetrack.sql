-- Suivi Garmin LiveTrack : nouvelle source de points (GARMIN) et nouveau type de trajet suivi (LIVETRACK).
-- identifier : l'identifiant de session (UUID, 36 caractères) ne tient plus dans 20.
-- access_token : jeton du lien de partage (secret ; jamais exposé par l'API).
ALTER TABLE tracked_trip DROP CONSTRAINT chk_trip_type;
ALTER TABLE tracked_trip MODIFY identifier VARCHAR(64) NOT NULL;
ALTER TABLE tracked_trip ADD COLUMN access_token VARCHAR(100) NULL AFTER identifier;
ALTER TABLE tracked_trip ADD CONSTRAINT chk_trip_type CHECK (type IN ('PLANE', 'BOAT', 'LIVETRACK'));

ALTER TABLE track_point DROP CONSTRAINT chk_point_source;
ALTER TABLE track_point ADD CONSTRAINT chk_point_source CHECK (source IN ('DEVICE', 'ADSB', 'AIS', 'GARMIN'));

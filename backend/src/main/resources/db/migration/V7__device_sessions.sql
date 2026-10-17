-- Sessions « Téléphone » : les points GPSLogger (source DEVICE) sont rattachés à un trajet de type DEVICE
-- (découpage automatique par silence, voir DeviceSessionService). Pas de reprise des points existants.
ALTER TABLE tracked_trip DROP CONSTRAINT chk_trip_type;
ALTER TABLE tracked_trip ADD CONSTRAINT chk_trip_type CHECK (type IN ('PLANE', 'BOAT', 'LIVETRACK', 'DEVICE'));
-- Plage de temps des points d'un trajet (dernier point d'une session, découpage).
CREATE INDEX idx_point_trip_time ON track_point (trip_id, recorded_at);

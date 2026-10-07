-- Précision (en mètres) annoncée par le téléphone pour chaque point : sert à écarter les points trop imprécis
-- à l'ajout, et à régler plus tard les filtres du tracé. NULL = précision inconnue (anciens points, ou non envoyée).
ALTER TABLE track_point
    ADD COLUMN accuracy_meters DECIMAL(7,1) NULL AFTER longitude,
    ADD CONSTRAINT chk_point_accuracy CHECK (accuracy_meters IS NULL OR accuracy_meters > 0);

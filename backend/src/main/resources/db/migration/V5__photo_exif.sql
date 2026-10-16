-- Prises de vue d'une photo (focale, ouverture, ISO, vitesse), lues dans l'original à l'envoi.
-- JSON prêt à l'emploi, par exemple {"focalLength":24,"aperture":1.8,"iso":100,"shutter":"1/250"} ; NULL si la photo n'a pas d'EXIF.
-- Aucun tri ni filtre ne porte dessus : un simple texte JSON suffit.
ALTER TABLE gallery_item
    ADD COLUMN exif_json VARCHAR(300) NULL AFTER height,
    ADD CONSTRAINT chk_item_exif_json CHECK (exif_json IS NULL OR JSON_VALID(exif_json));

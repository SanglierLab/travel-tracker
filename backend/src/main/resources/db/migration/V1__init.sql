-- travel-tracker 2.0.0 : schéma initial
-- Toutes les dates/heures sont en UTC (renseignées par l'application). gallery_date est un simple jour.

CREATE TABLE gallery (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    title         VARCHAR(200)  NOT NULL,
    place_name    VARCHAR(200)  NOT NULL,
    gallery_date  DATE          NOT NULL,
    latitude      DECIMAL(9,6)  NOT NULL,
    longitude     DECIMAL(9,6)  NOT NULL,
    created_at    DATETIME      NOT NULL,
    updated_at    DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_gallery_date (gallery_date, id),
    CONSTRAINT chk_gallery_lat CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_gallery_lon CHECK (longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Une seule table pour les trois types d'éléments : PHOTO, VIDEO, TEXT.
CREATE TABLE gallery_item (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    gallery_id         BIGINT       NOT NULL,
    sort_order         INT          NOT NULL,
    type               VARCHAR(10)  NOT NULL,
    text_markdown      TEXT         NULL,
    file_key           CHAR(36)     NULL,
    original_filename  VARCHAR(255) NULL,
    extension          VARCHAR(10)  NULL,
    size_bytes         BIGINT       NULL,
    width              INT          NULL,
    height             INT          NULL,
    created_at         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_file_key (file_key),
    KEY idx_item_gallery_order (gallery_id, sort_order),
    CONSTRAINT fk_item_gallery FOREIGN KEY (gallery_id) REFERENCES gallery (id) ON DELETE CASCADE,
    CONSTRAINT chk_item_type CHECK (type IN ('PHOTO', 'VIDEO', 'TEXT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vols et traversées suivis (le batch ADS-B / AIS viendra plus tard).
CREATE TABLE tracked_trip (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    type                 VARCHAR(10)  NOT NULL,
    label                VARCHAR(200) NOT NULL,
    scheduled_departure  DATETIME     NOT NULL,
    identifier           VARCHAR(20)  NOT NULL,
    status               VARCHAR(10)  NOT NULL DEFAULT 'PLANNED',
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_trip_type   CHECK (type   IN ('PLANE', 'BOAT')),
    CONSTRAINT chk_trip_status CHECK (status IN ('PLANNED', 'ACTIVE', 'FINISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Points de route. Supprimer un trajet suivi détache ses points (trip_id = NULL) ;
-- c'est le service qui les supprime si l'admin le demande.
CREATE TABLE track_point (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    source       VARCHAR(10)   NOT NULL,
    latitude     DECIMAL(9,6)  NOT NULL,
    longitude    DECIMAL(9,6)  NOT NULL,
    recorded_at  DATETIME(3)   NOT NULL,
    trip_id      BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    PRIMARY KEY (id),
    -- Idempotence : GPSLogger renvoie parfois les mêmes points. Un doublon est ignoré par l'API.
    UNIQUE KEY uk_point_dedup (source, recorded_at, latitude, longitude),
    KEY idx_point_trip (trip_id),
    CONSTRAINT fk_point_trip FOREIGN KEY (trip_id) REFERENCES tracked_trip (id) ON DELETE SET NULL,
    CONSTRAINT chk_point_source CHECK (source IN ('DEVICE', 'ADSB', 'AIS')),
    CONSTRAINT chk_point_lat    CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_point_lon    CHECK (longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

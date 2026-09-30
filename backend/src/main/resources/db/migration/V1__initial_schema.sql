-- =====================================================================
-- Travel Tracker 2.0 — schéma initial (MariaDB, utf8mb4)
-- =====================================================================

-- ---------------------------------------------------------------- gallery
CREATE TABLE gallery (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  gallery_date  DATE         NOT NULL,
  place         VARCHAR(160) NOT NULL,   -- "Tokyo"
  title         VARCHAR(200) NOT NULL,   -- "Palais impérial"
  latitude      DOUBLE       NOT NULL,
  longitude     DOUBLE       NOT NULL,
  published     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_gallery_date (gallery_date DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- element
-- Flux unique et ordonné : photos, vidéos et blocs de texte cohabitent.
CREATE TABLE element (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  gallery_id     BIGINT       NOT NULL,
  position       INT          NOT NULL,
  type           ENUM('PHOTO','VIDEO','TEXT') NOT NULL,

  -- TEXT
  markdown       TEXT         NULL,

  -- PHOTO / VIDEO  (chemins RELATIFS à media.dir)
  storage_path   VARCHAR(255) NULL,   -- fichier principal (jpg/png/mp4/mov)
  thumb_path     VARCHAR(255) NULL,
  medium_path    VARCHAR(255) NULL,
  heic_path      VARCHAR(255) NULL,   -- HEIC d'origine archivé
  original_name  VARCHAR(255) NULL,
  mime_type      VARCHAR(100) NULL,
  file_size      BIGINT       NULL,
  width          INT          NULL,
  height         INT          NULL,
  orientation    INT          NULL,
  duration_s     INT          NULL,   -- vidéos
  taken_at       DATETIME     NULL,   -- EXIF
  exif_latitude  DOUBLE       NULL,   -- EXIF GPS (aide au placement du marqueur)
  exif_longitude DOUBLE       NULL,
  caption        VARCHAR(255) NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_element_gallery (gallery_id, position),
  CONSTRAINT fk_element_gallery FOREIGN KEY (gallery_id)
    REFERENCES gallery (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------- trip
-- Vol (ADS-B) ou traversée (AIS) suivi automatiquement par un batch.
CREATE TABLE trip (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  type            ENUM('ADSB','AIS') NOT NULL,
  identifier      VARCHAR(32)  NOT NULL,   -- callsign (avion) / MMSI (bateau)
  label           VARCHAR(160) NULL,       -- "Paris → Tokyo"
  scheduled_start DATETIME     NOT NULL,   -- déclenche le batch
  status          ENUM('PLANNED','ACTIVE','FINISHED') NOT NULL DEFAULT 'PLANNED',
  started_at      DATETIME     NULL,
  finished_at     DATETIME     NULL,
  last_polled_at  DATETIME     NULL,
  color           VARCHAR(7)   NULL,       -- surcharge du style carte
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_trip_status (status, scheduled_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------ trace_point
CREATE TABLE trace_point (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  source       ENUM('DEVICE','ADSB','AIS') NOT NULL,
  trip_id      BIGINT   NULL,              -- renseigné pour ADSB / AIS
  measured_at  DATETIME NOT NULL,          -- horodatage de la position
  received_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  latitude     DOUBLE   NOT NULL,
  longitude    DOUBLE   NOT NULL,
  altitude_m   DOUBLE   NULL,
  speed_kmh    DOUBLE   NULL,
  heading_deg  DOUBLE   NULL,
  accuracy_m   DOUBLE   NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trace_dedup (source, measured_at, trip_id),
  KEY idx_trace_source_time (source, measured_at),
  KEY idx_trace_trip (trip_id, measured_at),
  CONSTRAINT fk_trace_trip FOREIGN KEY (trip_id)
    REFERENCES trip (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

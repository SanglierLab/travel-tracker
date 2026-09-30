-- =========================================================
-- Carnet de voyage - schéma initial (MariaDB, utf8mb4)
-- =========================================================

CREATE TABLE galerie (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  date_galerie   DATE         NOT NULL,
  lieu           VARCHAR(160) NOT NULL,              -- "Tokyo"
  titre          VARCHAR(200) NOT NULL,              -- "Palais impérial"
  latitude       DOUBLE       NOT NULL,
  longitude      DOUBLE       NOT NULL,
  publiee        BOOLEAN      NOT NULL DEFAULT TRUE, -- brouillon / visible
  creee_le       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifiee_le    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_galerie_date (date_galerie DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Un seul flux ordonné : photos, vidéos et blocs de texte cohabitent
CREATE TABLE element (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  galerie_id        BIGINT       NOT NULL,
  position          INT          NOT NULL,
  type              ENUM('PHOTO','VIDEO','TEXTE') NOT NULL,
  -- TEXTE
  texte_markdown    TEXT         NULL,
  -- PHOTO / VIDEO
  fichier_original  VARCHAR(255) NULL,
  fichier_plein     VARCHAR(255) NULL,
  fichier_miniature VARCHAR(255) NULL,
  largeur           INT          NULL,
  hauteur           INT          NULL,
  duree_s           INT          NULL,   -- vidéos
  taille_octets     BIGINT       NULL,
  prise_le          DATETIME     NULL,   -- date EXIF si dispo
  legende           VARCHAR(255) NULL,
  creee_le          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_element_galerie (galerie_id, position),
  CONSTRAINT fk_element_galerie FOREIGN KEY (galerie_id) REFERENCES galerie (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Trajets suivis automatiquement (vol ADS-B / traversée AIS)
CREATE TABLE trajet (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  type           ENUM('ADSB','AIS') NOT NULL,
  identifiant    VARCHAR(32)  NOT NULL,             -- callsign (avion) ou MMSI (bateau)
  libelle        VARCHAR(160) NULL,                 -- "Paris -> Tokyo"
  depart_prevu   DATETIME     NOT NULL,             -- déclenche le batch
  statut         ENUM('PLANIFIE','EN_COURS','TERMINE') NOT NULL DEFAULT 'PLANIFIE',
  demarre_le     DATETIME     NULL,
  termine_le     DATETIME     NULL,
  couleur        VARCHAR(7)   NULL,                 -- surcharge éventuelle du style carte
  creee_le       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifiee_le    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_trajet_statut (statut, depart_prevu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Points de position (fil du Petit Poucet)
CREATE TABLE trace_point (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  source       ENUM('DEVICE','ADSB','AIS') NOT NULL,
  trajet_id    BIGINT   NULL,                       -- renseigné pour ADSB / AIS
  mesure_le    DATETIME NOT NULL,                   -- horodatage de la position
  recu_le      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  latitude     DOUBLE   NOT NULL,
  longitude    DOUBLE   NOT NULL,
  altitude_m   DOUBLE   NULL,
  vitesse_kmh  DOUBLE   NULL,
  cap_deg      DOUBLE   NULL,
  precision_m  DOUBLE   NULL,
  PRIMARY KEY (id),
  KEY idx_trace_source_date (source, mesure_le),
  KEY idx_trace_trajet (trajet_id, mesure_le),
  CONSTRAINT fk_trace_trajet FOREIGN KEY (trajet_id) REFERENCES trajet (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

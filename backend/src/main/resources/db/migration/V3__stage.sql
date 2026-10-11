-- Étapes importantes du voyage : un lieu fixe ou un trajet (train, avion, bateau), à venir ou passé.
-- sort_order : ordre manuel (celui du tracé fléché chronologique). path_data : tracé GPX allégé, "lat,lon;lat,lon;..." (trajets seulement).
CREATE TABLE stage (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(200)  NOT NULL,
    kind             VARCHAR(10)   NOT NULL,
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    start_latitude   DECIMAL(9,6)  NOT NULL,
    start_longitude  DECIMAL(9,6)  NOT NULL,
    end_latitude     DECIMAL(9,6)  NULL,
    end_longitude    DECIMAL(9,6)  NULL,
    path_data        MEDIUMTEXT    NULL,
    sort_order       INT           NOT NULL,
    created_at       DATETIME      NOT NULL,
    updated_at       DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stage_order (sort_order, id),
    CONSTRAINT chk_stage_kind  CHECK (kind IN ('PLACE', 'TRAIN', 'PLANE', 'BOAT')),
    CONSTRAINT chk_stage_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_stage_start_lat CHECK (start_latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_stage_start_lon CHECK (start_longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_stage_end_lat   CHECK (end_latitude  IS NULL OR end_latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_stage_end_lon   CHECK (end_longitude IS NULL OR end_longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_stage_end_pair  CHECK ((end_latitude IS NULL) = (end_longitude IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

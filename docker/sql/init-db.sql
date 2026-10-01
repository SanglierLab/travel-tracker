-- À exécuter une seule fois, avec un compte administrateur MariaDB.
-- Remplacer le mot de passe. Flyway créera ensuite les tables au premier démarrage de l'API.
CREATE DATABASE IF NOT EXISTS traveltracker
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'traveltracker'@'localhost' IDENTIFIED BY 'CHANGE_ME';
CREATE USER IF NOT EXISTS 'traveltracker'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON traveltracker.* TO 'traveltracker'@'localhost';
GRANT ALL PRIVILEGES ON traveltracker.* TO 'traveltracker'@'127.0.0.1';
FLUSH PRIVILEGES;

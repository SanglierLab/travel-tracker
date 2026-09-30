package fr.sanglierlab.travel.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Vérifications et préparation au démarrage.
 *
 * Objectif : échouer immédiatement et clairement plutôt que de planter au
 * premier upload, trois semaines plus tard, depuis un hôtel à Kyoto.
 */
@Component
public class StartupInitializer {

    private static final Logger log = LoggerFactory.getLogger(StartupInitializer.class);

    private static final String DEFAULT_TOKEN    = "change-me";
    private static final String DEFAULT_ADMIN    = "admin:admin";

    private final AppProperties properties;
    private final Environment environment;

    public StartupInitializer(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void initialize() throws IOException {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        prepareDirectory(Paths.get(properties.media().dir()), "médias");
        prepareDirectory(Paths.get(properties.tmpDir()), "fichiers temporaires");

        checkSecrets(production);
        checkFfmpeg();

        log.info("Travel Tracker démarré — médias : {}, page : {} galeries, profil : {}",
                Paths.get(properties.media().dir()).toAbsolutePath().normalize(),
                properties.pageSize(),
                production ? "prod" : String.join(",", environment.getActiveProfiles()));
    }

    // ------------------------------------------------------------------ utils

    private void prepareDirectory(Path directory, String label) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        Files.createDirectories(absolute);
        if (!Files.isWritable(absolute)) {
            throw new IllegalStateException(
                    "Répertoire des " + label + " non accessible en écriture : " + absolute);
        }
        log.debug("Répertoire des {} prêt : {}", label, absolute);
    }

    private void checkSecrets(boolean production) {
        if (DEFAULT_TOKEN.equals(properties.ingestToken())) {
            String message = "app.ingest-token est resté à sa valeur par défaut « "
                    + DEFAULT_TOKEN + " ». Définissez TT_INGEST_TOKEN.";
            if (production) {
                throw new IllegalStateException(message);
            }
            log.warn("{} (toléré hors production)", message);
        }

        if (properties.adminUsers().contains(DEFAULT_ADMIN)) {
            String message = "Le compte administrateur par défaut « " + DEFAULT_ADMIN
                    + " » est actif. Définissez TT_ADMIN_USERS.";
            if (production) {
                throw new IllegalStateException(message);
            }
            log.warn("{} (toléré hors production)", message);
        }

        // Déclenche la validation du format login:motdepasse dès le démarrage
        properties.accounts();
    }

    private void checkFfmpeg() {
        Path binary = Paths.get(properties.ffmpeg().path());
        if (binary.isAbsolute() && !Files.isExecutable(binary)) {
            log.warn("ffmpeg introuvable à l'emplacement {} : les vignettes vidéo "
                    + "ne pourront pas être générées.", binary);
        }
    }
}

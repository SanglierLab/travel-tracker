package fr.sanglierlab.travel.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

/**
 * Configuration applicative, préfixe {@code app} dans application.yml.
 *
 * Toutes les valeurs sont surchargeables par variable d'environnement
 * (TT_ADMIN_USERS, TT_INGEST_TOKEN, TT_MEDIA_DIR...), ce qui permet de tout
 * régler depuis l'interface Docker du NAS sans reconstruire l'image.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        @NotBlank String siteTitle,

        /**
         * Comptes administrateurs, au format {@code login:motdepasse}.
         * Spring convertit automatiquement une variable d'environnement
         * séparée par des virgules en liste.
         *
         * Le mot de passe peut être :
         *  - en clair            : "voyageur:monSecret"
         *  - déjà encodé bcrypt  : "voyageur:{bcrypt}$2a$10$..."
         */
        @NotEmpty List<String> adminUsers,

        /** Jeton porteur attendu par l'API d'ingestion des positions. */
        @NotBlank String ingestToken,

        /** Nombre de galeries par page sur la timeline publique. */
        @Min(1) @Max(50) int pageSize,

        /** Répertoire des fichiers temporaires d'upload. */
        @NotBlank String tmpDir,

        @NotNull Media media,
        @NotNull Ffmpeg ffmpeg,
        @NotNull Trip trip
) {

    // ------------------------------------------------------------------ media
    public record Media(
            /** Racine du stockage. Chemins relatifs stockés en base. */
            @NotBlank String dir,
            @Min(100) int thumbWidth,
            @Min(400) int mediumWidth,
            float jpegQuality,
            @NotEmpty List<String> acceptedImageMime,
            @NotEmpty List<String> acceptedVideoMime,
            @Min(1) long maxFileSizeMb
    ) {
        public long maxFileSizeBytes() {
            return maxFileSizeMb * 1024L * 1024L;
        }

        public boolean isImage(String mimeType) {
            return mimeType != null
                    && acceptedImageMime.contains(mimeType.toLowerCase(Locale.ROOT));
        }

        public boolean isVideo(String mimeType) {
            return mimeType != null
                    && acceptedVideoMime.contains(mimeType.toLowerCase(Locale.ROOT));
        }

        public boolean isAccepted(String mimeType) {
            return isImage(mimeType) || isVideo(mimeType);
        }
    }

    // ----------------------------------------------------------------- ffmpeg
    public record Ffmpeg(
            @NotBlank String path,
            /** Chemin de ffprobe, livré avec ffmpeg. Déduit de {@code path} si absent. */
            String probePath,
            @Min(5) int timeoutS,
            @Min(0) int frameAtSecond
    ) {
        public Ffmpeg {
            if (probePath == null || probePath.isBlank()) {
                probePath = path.replace("ffmpeg", "ffprobe");
            }
        }
    }

    // ------------------------------------------------------------------- trip
    public record Trip(
            /** Période d'interrogation des sources ADS-B et AIS. */
            @NotBlank String pollingInterval,
            /**
             * Durée au-delà de laquelle un trajet resté ouvert est clos d'office.
             * Sans ce garde-fou, un vol dont l'arrivée n'a pas été détectée
             * interrogerait le fournisseur de données sans fin.
             */
            @Min(1) long maximumDurationHours
    ) {}

    // ---------------------------------------------------------- comptes admin
    /** Un compte administrateur une fois la chaîne de conf découpée. */
    public record AdminAccount(String username, String password) {}

    /**
     * Découpe {@link #adminUsers()} en comptes exploitables.
     * Le mot de passe peut contenir des « : », seul le premier sépare.
     */
    public List<AdminAccount> accounts() {
        return adminUsers.stream()
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .map(entry -> {
                    int sep = entry.indexOf(':');
                    if (sep <= 0 || sep == entry.length() - 1) {
                        throw new IllegalStateException(
                                "app.admin-users : entrée invalide « " + entry
                                        + " », format attendu « login:motdepasse »");
                    }
                    return new AdminAccount(
                            entry.substring(0, sep).trim(),
                            entry.substring(sep + 1));
                })
                .toList();
    }
}

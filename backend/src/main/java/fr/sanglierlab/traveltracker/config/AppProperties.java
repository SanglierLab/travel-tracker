package fr.sanglierlab.traveltracker.config;

import fr.sanglierlab.traveltracker.track.TrackSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Configuration applicative (préfixe {@code app}), lue dans le fichier externe
 * {@code /app/config/application.yml}. Le démarrage échoue si un secret manque.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotEmpty List<@Valid Admin> admins,
        @NotBlank @Size(min = 4, message = "le token d'API doit faire au moins 24 caractères") String apiToken,
        @NotNull @DefaultValue("/data/media") Path mediaDir,
        @NotNull @DefaultValue("30MB") DataSize maxPhotoSize,
        @NotNull @DefaultValue("500MB") DataSize maxVideoSize,
        @Min(1) @Max(100) @DefaultValue("10") int pageSize,
        @Min(1) @DefaultValue("6") int trackGapHours,
        @Min(1) @Max(100000) @DefaultValue("100") int trackMaxAccuracyMeters,
        @Min(1) @DefaultValue("5") int maxLoginFailures,
        @NotNull @DefaultValue("15m") Duration loginLockDuration,
        @NotBlank @DefaultValue("ffmpeg") String ffmpegPath,
        /** Une étape à venir qui commence dans ce nombre de jours (ou moins) est montrée en tête du fil, hors du bloc repliable. 0 : aucune. */
        @Min(0) @Max(365) @DefaultValue("7") int upcomingDays,
        /** Au-delà de ce délai sans nouvelle position, le marqueur « dernière position » de la carte passe en style « en retard ». */
        @NotNull @Valid @DefaultValue LateAfter trackLateAfter) {

    /** Délai sans position avant d'être « en retard », par source (une durée : 10m, 90s...). */
    public record LateAfter(
            @NotNull @DefaultValue("10m") Duration device,
            @NotNull @DefaultValue("5m") Duration adsb,
            @NotNull @DefaultValue("20m") Duration ais) {

        public Duration of(TrackSource source) {
            return switch (source) {
                case DEVICE -> device;
                case ADSB -> adsb;
                case AIS -> ais;
            };
        }
    }

    /** Compte administrateur : mot de passe haché en bcrypt. */
    public record Admin(@NotBlank String username, @NotBlank String passwordHash) {
    }
}

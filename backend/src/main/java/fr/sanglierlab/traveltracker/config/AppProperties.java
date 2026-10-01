package fr.sanglierlab.traveltracker.config;

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
        @NotBlank @Size(min = 24, message = "le token d'API doit faire au moins 24 caractères") String apiToken,
        @NotNull @DefaultValue("/data/media") Path mediaDir,
        @NotNull @DefaultValue("30MB") DataSize maxPhotoSize,
        @NotNull @DefaultValue("500MB") DataSize maxVideoSize,
        @Min(1) @Max(100) @DefaultValue("10") int pageSize,
        @Min(1) @DefaultValue("6") int trackGapHours,
        @Min(1) @DefaultValue("5") int maxLoginFailures,
        @NotNull @DefaultValue("15m") Duration loginLockDuration,
        @NotBlank @DefaultValue("ffmpeg") String ffmpegPath) {

    /** Compte administrateur : mot de passe haché en bcrypt. */
    public record Admin(@NotBlank String username, @NotBlank String passwordHash) {
    }
}

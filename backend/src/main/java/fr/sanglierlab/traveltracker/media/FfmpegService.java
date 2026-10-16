package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Appels à ffmpeg : image représentative d'une vidéo, et conversion d'une image WebP en JPEG
 * (Java ne lit pas le WebP nativement ; ffmpeg est déjà là, inutile d'ajouter une bibliothèque).
 * Un seul appel à la fois.
 */
@Service
public class FfmpegService {

    private static final long TIMEOUT_SECONDS = 60;
    private static final int MAX_LOGGED_BYTES = 4096;

    private final String ffmpegPath;

    public FfmpegService(AppProperties properties) {
        this.ffmpegPath = properties.ffmpegPath();
    }

    /**
     * Extrait une image représentative d'une vidéo (filtre « thumbnail » de ffmpeg) vers un JPEG.
     * L'analyse est limitée aux 5 premières secondes pour rester rapide sur un petit NAS.
     */
    public synchronized void extractVideoFrame(Path video, Path targetJpeg) throws IOException {
        run(List.of(ffmpegPath, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-t", "5", "-i", video.toAbsolutePath().toString(),
                "-vf", "thumbnail", "-frames:v", "1", "-q:v", "2",
                targetJpeg.toAbsolutePath().toString()));
    }

    /** Convertit une image (WebP) en JPEG pleine qualité. */
    public synchronized void convertToJpeg(Path image, Path targetJpeg) throws IOException {
        run(List.of(ffmpegPath, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-i", image.toAbsolutePath().toString(),
                "-frames:v", "1", "-q:v", "2",
                targetJpeg.toAbsolutePath().toString()));
    }

    /**
     * Première ligne de « ffmpeg -version » réduite à la version (ex. « 6.1.1 »), ou vide si ffmpeg n'est pas utilisable.
     * Sans effet sur les traitements en cours (pas de verrou) : un simple appel de quelques millisecondes.
     */
    public Optional<String> version() {
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version").redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String first = new String(process.getInputStream().readNBytes(MAX_LOGGED_BYTES), StandardCharsets.UTF_8).lines().findFirst().orElse("");
            if (process.exitValue() != 0 || first.isBlank()) {
                return Optional.empty();
            }
            String[] words = first.strip().split("\\s+");
            return Optional.of(words.length >= 3 && "version".equals(words[1]) ? words[2] : first.strip());
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private void run(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        Path target = Path.of(command.get(command.size() - 1));
        try {
            // On attend d'abord la fin du processus (avec délai maximal) : avec « -loglevel error »,
            // la sortie est minuscule et ne peut pas bloquer le processus.
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("ffmpeg : délai dépassé (" + TIMEOUT_SECONDS + " s)");
            }
            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readNBytes(MAX_LOGGED_BYTES), StandardCharsets.UTF_8);
                throw new IOException("ffmpeg a échoué (code " + process.exitValue() + ") : " + output.strip());
            }
            if (!Files.exists(target) || Files.size(target) == 0) {
                throw new IOException("ffmpeg n'a produit aucune image");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("ffmpeg interrompu", e);
        }
    }
}

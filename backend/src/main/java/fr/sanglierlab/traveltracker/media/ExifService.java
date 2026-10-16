package fr.sanglierlab.traveltracker.media;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prises de vue d'une photo (focale, ouverture, ISO, vitesse), lues dans l'EXIF de l'original avec metadata-extractor.
 *
 * <p>Le résultat est un JSON prêt à l'emploi, stocké tel quel en base et renvoyé tel quel au front :
 * {@code {"focalLength":24,"aperture":1.8,"iso":100,"shutter":"1/250","takenAt":"2026-10-12T16:03:21"}} (mm, f/, ISO, secondes, heure locale de prise de vue). Chaque clé manque si la valeur est
 * absente ou absurde. Une photo sans aucune de ces données donne un résultat vide.
 *
 * <p>C'est un « plus » : toute erreur de lecture (EXIF corrompu, format sans EXIF) est ignorée, l'envoi de la photo n'échoue jamais pour cela.
 */
@Service
public class ExifService {

    private static final Pattern DATE_TIME = Pattern.compile("^(\\d{4})[:-](\\d{2})[:-](\\d{2})[ T](\\d{2}):(\\d{2}):(\\d{2})");

    private static final DateTimeFormatter TAKEN_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);

    private static final Logger log = LoggerFactory.getLogger(ExifService.class);

    /** @return le JSON des prises de vue, ou vide si la photo n'en contient pas ou si l'EXIF est illisible */
    public Optional<String> extractJson(Path photo) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(photo.toFile());
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exif == null) {
                return Optional.empty();
            }
            return toJson(value(exif.getRational(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)),
                    value(exif.getRational(ExifSubIFDDirectory.TAG_FNUMBER)),
                    value(exif.getRational(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)),
                    exif.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
                    takenAt(exif));
        } catch (Exception e) {
            // ImageProcessingException, IOException, ou valeur inattendue d'un fichier corrompu : sans conséquence.
            log.debug("EXIF illisible ({}) : {}", photo.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Date et heure de prise de vue : DateTimeOriginal (« 2026:10:12 16:03:21 »), à défaut DateTimeDigitized. L'EXIF ne contient en général
     * <b>aucun fuseau horaire</b> (seulement l'heure de l'horloge de l'appareil) : on la garde telle quelle, comme heure locale du lieu.
     * La chaîne est lue brute (pas de conversion en {@code Date}, qui lui appliquerait le fuseau du serveur).
     */
    private static LocalDateTime takenAt(ExifSubIFDDirectory exif) {
        for (int tag : new int[]{ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED}) {
            LocalDateTime parsed = parseDateTime(exif.getString(tag));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    /** « 2026:10:12 16:03:21 » (ou avec « - » et « T ») ; null si absent, mal formé ou absurde (« 0000:00:00 00:00:00 »). */
    static LocalDateTime parseDateTime(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = DATE_TIME.matcher(text.strip());
        if (!matcher.find()) {
            return null;
        }
        try {
            LocalDateTime value = LocalDateTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)),
                    Integer.parseInt(matcher.group(6)));
            return value.getYear() >= 1990 && value.getYear() <= 2100 ? value : null;
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static Double value(Rational rational) {
        return rational == null ? null : rational.doubleValue();
    }

    /**
     * Construit le JSON à partir des valeurs brutes (secondes, f/, mm, ISO). Valeurs nulles, infinies ou hors plage : ignorées.
     */
    static Optional<String> toJson(Double exposureSeconds, Double aperture, Double focalLength, Integer iso, LocalDateTime takenAt) {
        StringBuilder json = new StringBuilder("{");
        append(json, "focalLength", valid(focalLength, 0, 5000) ? number(focalLength) : null);
        append(json, "aperture", valid(aperture, 0, 100) ? number(aperture) : null);
        append(json, "iso", iso != null && iso >= 1 && iso <= 10_000_000 ? Integer.toString(iso) : null);
        append(json, "shutter", valid(exposureSeconds, 0, 100_000) ? "\"" + shutter(exposureSeconds) + "\"" : null);
        // Heure locale du lieu, sans fuseau : « 2026-10-12T16:03:21 » (secondes toujours présentes, format fixe).
        append(json, "takenAt", takenAt == null ? null : "\"" + takenAt.format(TAKEN_AT_FORMAT) + "\"");
        if (json.length() == 1) {
            return Optional.empty();
        }
        return Optional.of(json.append('}').toString());
    }

    private static void append(StringBuilder json, String key, String jsonValue) {
        if (jsonValue == null) {
            return;
        }
        if (json.length() > 1) {
            json.append(',');
        }
        json.append('"').append(key).append("\":").append(jsonValue);
    }

    private static boolean valid(Double value, double exclusiveMin, double max) {
        return value != null && !value.isNaN() && !value.isInfinite() && value > exclusiveMin && value <= max;
    }

    /** Un chiffre après la virgule au plus, sans « .0 » inutile : 24 → 24, 1.8 → 1.8, 5.6000001 → 5.6. */
    static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** Vitesse d'obturation lisible, en secondes : 0.004 → « 1/250 », 0.8 → « 0.8 », 2 → « 2 ». */
    static String shutter(double seconds) {
        if (seconds >= 0.4) {
            return number(seconds);
        }
        return "1/" + Math.round(1 / seconds);
    }
}

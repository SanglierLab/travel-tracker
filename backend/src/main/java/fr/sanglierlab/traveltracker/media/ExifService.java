package fr.sanglierlab.traveltracker.media;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Prises de vue d'une photo (focale, ouverture, ISO, vitesse), lues dans l'EXIF de l'original avec metadata-extractor.
 *
 * <p>Le résultat est un JSON prêt à l'emploi, stocké tel quel en base et renvoyé tel quel au front :
 * {@code {"focalLength":24,"aperture":1.8,"iso":100,"shutter":"1/250"}} (mm, f/, ISO, secondes). Chaque clé manque si la valeur est
 * absente ou absurde. Une photo sans aucune de ces données donne un résultat vide.
 *
 * <p>C'est un « plus » : toute erreur de lecture (EXIF corrompu, format sans EXIF) est ignorée, l'envoi de la photo n'échoue jamais pour cela.
 */
@Service
public class ExifService {

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
                    exif.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
        } catch (Exception e) {
            // ImageProcessingException, IOException, ou valeur inattendue d'un fichier corrompu : sans conséquence.
            log.debug("EXIF illisible ({}) : {}", photo.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private static Double value(Rational rational) {
        return rational == null ? null : rational.doubleValue();
    }

    /**
     * Construit le JSON à partir des valeurs brutes (secondes, f/, mm, ISO). Valeurs nulles, infinies ou hors plage : ignorées.
     */
    static Optional<String> toJson(Double exposureSeconds, Double aperture, Double focalLength, Integer iso) {
        StringBuilder json = new StringBuilder("{");
        append(json, "focalLength", valid(focalLength, 0, 5000) ? number(focalLength) : null);
        append(json, "aperture", valid(aperture, 0, 100) ? number(aperture) : null);
        append(json, "iso", iso != null && iso >= 1 && iso <= 10_000_000 ? Integer.toString(iso) : null);
        append(json, "shutter", valid(exposureSeconds, 0, 100_000) ? "\"" + shutter(exposureSeconds) + "\"" : null);
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

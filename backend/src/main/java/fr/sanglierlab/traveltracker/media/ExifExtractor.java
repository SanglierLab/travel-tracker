package fr.sanglierlab.traveltracker.media;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;


@Component
public class ExifExtractor {

    private static final Logger log = LoggerFactory.getLogger(ExifExtractor.class);

    public PhotoExif extract(Path original) {
        // Buffer volontairement petit : metadata-extractor lit séquentiellement,
        // les segments EXIF font quelques Ko, inutile de charger des Mo.
        try (InputStream in = new BufferedInputStream(Files.newInputStream(original), 16 * 1024)) {

            Metadata metadata = ImageMetadataReader.readMetadata(in, Files.size(original));

            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

            if (sub == null && ifd0 == null) {
                return PhotoExif.EMPTY; // PNG, WebP sans EXIF, capture d'écran…
            }

            return new PhotoExif(
                    integer(sub, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
                    dbl(sub,     ExifSubIFDDirectory.TAG_FOCAL_LENGTH),
                    integer(sub, ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH),
                    dbl(sub,     ExifSubIFDDirectory.TAG_FNUMBER),
                    str(sub,     ExifSubIFDDirectory.TAG_EXPOSURE_TIME),   // déjà formaté "1/250 sec"
                    dbl(sub,     ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
                    takenAt(sub)
            );

        } catch (ImageProcessingException | IOException e) {
            // Jamais bloquant : un upload ne doit pas échouer pour un EXIF corrompu
            log.warn("Lecture EXIF impossible pour {} : {}", original, e.getMessage());
            return PhotoExif.EMPTY;
        }
    }

    private static Integer integer(com.drew.metadata.Directory d, int tag) {
        return (d == null || !d.containsTag(tag)) ? null : d.getInteger(tag);
    }

    private static Double dbl(com.drew.metadata.Directory d, int tag) {
        if (d == null || !d.containsTag(tag)) return null;
        try { return d.getDouble(tag); } catch (Exception e) { return null; }
    }

    private static String str(com.drew.metadata.Directory d, int tag) {
        return (d == null || !d.containsTag(tag)) ? null : d.getDescription(tag);
    }

    private static String trim(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Instant takenAt(ExifSubIFDDirectory sub) {
        if (sub == null) return null;
        Date d = sub.getDateOriginal(TimeZone.getDefault()); // TAG_DATETIME_ORIGINAL
        return d == null ? null : d.toInstant();
    }
}
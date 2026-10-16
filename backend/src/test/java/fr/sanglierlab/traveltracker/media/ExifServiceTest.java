package fr.sanglierlab.traveltracker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExifServiceTest {

    private final ExifService service = new ExifService();

    @TempDir
    Path dir;

    // --- Mise en forme (sans lire de fichier) ---

    @Test
    void produitUnJsonCompletEtCompact() {
        assertThat(ExifService.toJson(1 / 250.0, 1.8, 24.0, 100, null)).contains("{\"focalLength\":24,\"aperture\":1.8,\"iso\":100,\"shutter\":\"1/250\"}");
    }

    @Test
    void arrondiLesValeursAUneDecimale() {
        assertThat(ExifService.number(5.6000001)).isEqualTo("5.6");
        assertThat(ExifService.number(24.0)).isEqualTo("24");
        assertThat(ExifService.number(6.9)).isEqualTo("6.9");
        assertThat(ExifService.number(1.84)).isEqualTo("1.8");
    }

    @Test
    void formateLaVitesseEnSecondes() {
        assertThat(ExifService.shutter(0.004)).isEqualTo("1/250");
        assertThat(ExifService.shutter(1 / 4000.0)).isEqualTo("1/4000");
        assertThat(ExifService.shutter(1 / 3.0)).isEqualTo("1/3");
        assertThat(ExifService.shutter(0.8)).isEqualTo("0.8");
        assertThat(ExifService.shutter(2.0)).isEqualTo("2");
        assertThat(ExifService.shutter(30.0)).isEqualTo("30");
    }

    @Test
    void ignoreLesValeursAbsentesOuAbsurdes() {
        assertThat(ExifService.toJson(null, 2.8, null, null, null)).contains("{\"aperture\":2.8}");
        assertThat(ExifService.toJson(0.0, Double.NaN, Double.POSITIVE_INFINITY, 0, null)).isEmpty();
        assertThat(ExifService.toJson(-1.0, -2.0, -3.0, -4, null)).isEmpty();
        assertThat(ExifService.toJson(null, null, null, null, null)).isEmpty();
        assertThat(ExifService.toJson(null, null, 50.0, 1600, null)).contains("{\"focalLength\":50,\"iso\":1600}");
    }

    @Test
    void ajouteLaDateDePriseDeVueEnHeureLocaleSansFuseau() {
        assertThat(ExifService.toJson(null, null, null, null, LocalDateTime.of(2026, 10, 12, 16, 3, 21)))
                .contains("{\"takenAt\":\"2026-10-12T16:03:21\"}");
        // Les secondes sont toujours écrites : format fixe.
        assertThat(ExifService.toJson(null, null, null, null, LocalDateTime.of(2026, 10, 12, 16, 3, 0)))
                .contains("{\"takenAt\":\"2026-10-12T16:03:00\"}");
        assertThat(ExifService.toJson(1 / 250.0, 1.8, 24.0, 100, LocalDateTime.of(2026, 10, 12, 16, 3, 21)))
                .contains("{\"focalLength\":24,\"aperture\":1.8,\"iso\":100,\"shutter\":\"1/250\",\"takenAt\":\"2026-10-12T16:03:21\"}");
    }

    @Test
    void litLaDateExifEtIgnoreLesValeursAbsurdes() {
        assertThat(ExifService.parseDateTime("2026:10:12 16:03:21")).isEqualTo(LocalDateTime.of(2026, 10, 12, 16, 3, 21));
        assertThat(ExifService.parseDateTime(" 2026:10:12 16:03:21\0")).isEqualTo(LocalDateTime.of(2026, 10, 12, 16, 3, 21));
        assertThat(ExifService.parseDateTime("2026-10-12T16:03:21")).isEqualTo(LocalDateTime.of(2026, 10, 12, 16, 3, 21));
        assertThat(ExifService.parseDateTime("0000:00:00 00:00:00")).isNull(); // valeur « inconnue » d'appareils sans horloge
        assertThat(ExifService.parseDateTime("2026:13:45 25:61:61")).isNull();
        assertThat(ExifService.parseDateTime("1970:01:01 00:00:00")).isNull();
        assertThat(ExifService.parseDateTime("hier")).isNull();
        assertThat(ExifService.parseDateTime("")).isNull();
        assertThat(ExifService.parseDateTime(null)).isNull();
    }

    // --- Lecture d'un vrai fichier JPEG ---

    @Test
    void litLesPrisesDeVueDUnJpegAvecExif() throws IOException {
        Path photo = dir.resolve("exif.jpg");
        Files.write(photo, jpegWithExif(1, 250, 18, 10, 24, 1, 200, "2026:10:12 16:03:21"));

        assertThat(service.extractJson(photo))
                .contains("{\"focalLength\":24,\"aperture\":1.8,\"iso\":200,\"shutter\":\"1/250\",\"takenAt\":\"2026-10-12T16:03:21\"}");
    }

    @Test
    void laDateDePriseDeVueNEstJamaisDecaleeParLeFuseauDuServeur() throws IOException {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        try {
            for (String zone : new String[]{"Asia/Tokyo", "America/Los_Angeles", "UTC"}) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
                Path photo = dir.resolve("tz-" + zone.replace("/", "_") + ".jpg");
                Files.write(photo, jpegWithExif(1, 250, 18, 10, 24, 1, 200, "2026:10:12 16:03:21"));
                assertThat(service.extractJson(photo)).as(zone).hasValueSatisfying(json -> assertThat(json).contains("\"takenAt\":\"2026-10-12T16:03:21\""));
            }
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test
    void sansDateDePriseDeVueLesAutresDonneesRestent() throws IOException {
        Path photo = dir.resolve("nodate.jpg");
        Files.write(photo, jpegWithExif(1, 250, 18, 10, 24, 1, 200, null));

        assertThat(service.extractJson(photo)).contains("{\"focalLength\":24,\"aperture\":1.8,\"iso\":200,\"shutter\":\"1/250\"}");
    }

    @Test
    void unJpegSansExifDonneVide() throws IOException {
        Path photo = dir.resolve("plain.jpg");
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "jpg", photo.toFile());

        assertThat(service.extractJson(photo)).isEmpty();
    }

    @Test
    void unFichierIllisibleOuAbsentNeLevePasD_erreur() throws IOException {
        Path garbage = dir.resolve("garbage.jpg");
        Files.write(garbage, new byte[]{1, 2, 3, 4, 5});

        assertThat(service.extractJson(garbage)).isEqualTo(Optional.empty());
        assertThat(service.extractJson(dir.resolve("absent.jpg"))).isEmpty();
    }

    @Test
    void unExifCorrompuNeLevePasD_erreur() throws IOException {
        byte[] jpeg = jpegWithExif(1, 250, 18, 10, 24, 1, 200, "2026:10:12 16:03:21");
        // Coupe le fichier au milieu du segment EXIF.
        Path photo = dir.resolve("cut.jpg");
        Files.write(photo, java.util.Arrays.copyOf(jpeg, 60));

        assertThatCode(() -> service.extractJson(photo)).doesNotThrowAnyException();
    }

    /**
     * JPEG minimal avec un segment EXIF écrit à la main : exposition, ouverture et focale (rationnels), ISO (entier court) et, si
     * {@code dateTimeOriginal} n'est pas nul, la date de prise de vue (texte de 19 caractères, « AAAA:MM:JJ HH:MM:SS »).
     */
    static byte[] jpegWithExif(int expNum, int expDen, int fNum, int fDen, int focalNum, int focalDen, int iso, String dateTimeOriginal)
            throws IOException {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "jpg", plain);
        byte[] image = plain.toByteArray();

        boolean withDate = dateTimeOriginal != null;
        int entries = withDate ? 5 : 4;
        int dataStart = 26 + 2 + entries * 12 + 4; // après le sous-IFD EXIF
        int expOffset = dataStart;
        int fOffset = dataStart + 8;
        int dateOffset = dataStart + 16;
        int focalOffset = dataStart + 16 + (withDate ? 20 : 0);

        ByteBuffer tiff = ByteBuffer.allocate(focalOffset + 8); // gros-boutiste par défaut
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 0x002A).putInt(8);
        // IFD0 à 8 : une entrée, le pointeur vers le sous-IFD EXIF (à 26).
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x8769).putShort((short) 4).putInt(1).putInt(26);
        tiff.putInt(0);
        // Sous-IFD EXIF à 26 : entrées triées par étiquette, données ensuite.
        tiff.putShort((short) entries);
        tiff.putShort((short) 0x829A).putShort((short) 5).putInt(1).putInt(expOffset);   // ExposureTime
        tiff.putShort((short) 0x829D).putShort((short) 5).putInt(1).putInt(fOffset);     // FNumber
        tiff.putShort((short) 0x8827).putShort((short) 3).putInt(1).putShort((short) iso).putShort((short) 0); // ISO
        if (withDate) {
            tiff.putShort((short) 0x9003).putShort((short) 2).putInt(20).putInt(dateOffset); // DateTimeOriginal (ASCII, 19 + 0 final)
        }
        tiff.putShort((short) 0x920A).putShort((short) 5).putInt(1).putInt(focalOffset); // FocalLength
        tiff.putInt(0);
        tiff.putInt(expNum).putInt(expDen);
        tiff.putInt(fNum).putInt(fDen);
        if (withDate) {
            byte[] date = new byte[20];
            byte[] text = dateTimeOriginal.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(text, 0, date, 0, Math.min(19, text.length));
            tiff.put(date);
        }
        tiff.putInt(focalNum).putInt(focalDen);
        byte[] tiffBytes = tiff.array();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(image, 0, 2); // SOI
        int length = 2 + 6 + tiffBytes.length;
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.write(tiffBytes);
        out.write(image, 2, image.length - 2);
        return out.toByteArray();
    }
}

package fr.sanglierlab.traveltracker.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Reconnaît JPEG, PNG, WebP et MP4 à partir des premiers octets du fichier (« magic bytes »). */
public final class FileTypeDetector {

    /** Nombre d'octets à lire pour la détection. */
    public static final int HEADER_SIZE = 16;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private FileTypeDetector() {
    }

    public static Optional<MediaFormat> detect(InputStream in) throws IOException {
        return detect(in.readNBytes(HEADER_SIZE));
    }

    public static Optional<MediaFormat> detect(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return Optional.of(MediaFormat.JPEG);
        }
        if (startsWith(h, PNG_SIGNATURE)) {
            return Optional.of(MediaFormat.PNG);
        }
        if (h.length >= 12 && "RIFF".equals(ascii(h, 0, 4)) && "WEBP".equals(ascii(h, 8, 4))) {
            return Optional.of(MediaFormat.WEBP);
        }
        // MP4 : une boîte « ftyp » à l'offset 4, suivie de la marque (brand). Le QuickTime (« qt  »),
        // l'HEIC, etc. sont volontairement refusés.
        if (h.length >= 12 && "ftyp".equals(ascii(h, 4, 4)) && isMp4Brand(ascii(h, 8, 4))) {
            return Optional.of(MediaFormat.MP4);
        }
        return Optional.empty();
    }

    private static boolean isMp4Brand(String brand) {
        return brand.startsWith("iso") || brand.startsWith("mp4") || brand.startsWith("avc")
                || brand.startsWith("M4V") || brand.equals("dash");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.ISO_8859_1);
    }
}

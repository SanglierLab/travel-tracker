package fr.sanglierlab.traveltracker.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeDetectorTest {

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    @Test
    void reconnaitUnJpeg() {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
        assertThat(FileTypeDetector.detect(header)).contains(MediaFormat.JPEG);
    }

    @Test
    void reconnaitUnPng() {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        assertThat(FileTypeDetector.detect(header)).contains(MediaFormat.PNG);
    }

    @Test
    void reconnaitUnWebp() {
        byte[] header = concat(ascii("RIFF"), new byte[]{1, 2, 3, 4}, ascii("WEBP"));
        assertThat(FileTypeDetector.detect(header)).contains(MediaFormat.WEBP);
    }

    @Test
    void refuseUnFichierRiffQuiNEstPasUnWebp() {
        byte[] wav = concat(ascii("RIFF"), new byte[]{1, 2, 3, 4}, ascii("WAVE"));
        assertThat(FileTypeDetector.detect(wav)).isEmpty();
    }

    @Test
    void reconnaitUnMp4() {
        byte[] header = concat(new byte[]{0, 0, 0, 24}, ascii("ftyp"), ascii("isom"));
        assertThat(FileTypeDetector.detect(header)).contains(MediaFormat.MP4);
        byte[] mp42 = concat(new byte[]{0, 0, 0, 24}, ascii("ftyp"), ascii("mp42"));
        assertThat(FileTypeDetector.detect(mp42)).contains(MediaFormat.MP4);
    }

    @Test
    void refuseQuickTimeEtHeic() {
        byte[] mov = concat(new byte[]{0, 0, 0, 20}, ascii("ftyp"), ascii("qt  "));
        byte[] heic = concat(new byte[]{0, 0, 0, 24}, ascii("ftyp"), ascii("heic"));
        assertThat(FileTypeDetector.detect(mov)).isEmpty();
        assertThat(FileTypeDetector.detect(heic)).isEmpty();
    }

    @Test
    void refuseLeRestePeuImporteLExtension() {
        assertThat(FileTypeDetector.detect(ascii("<?php echo 1; ?>"))).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[0])).isEmpty();
    }
}

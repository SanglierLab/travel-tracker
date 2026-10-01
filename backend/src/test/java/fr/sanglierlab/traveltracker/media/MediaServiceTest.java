package fr.sanglierlab.traveltracker.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaServiceTest {

    @Test
    void nettoieLeNomDeFichier() {
        assertThat(MediaService.cleanFilename("C:\\Users\\moi\\photo 東京.jpg", MediaFormat.JPEG)).isEqualTo("photo 東京.jpg");
        assertThat(MediaService.cleanFilename("../../etc/passwd", MediaFormat.JPEG)).isEqualTo("passwd");
        assertThat(MediaService.cleanFilename("  ", MediaFormat.MP4)).isEqualTo("media.mp4");
        assertThat(MediaService.cleanFilename(null, MediaFormat.PNG)).isEqualTo("media.png");
        assertThat(MediaService.cleanFilename("a".repeat(400), MediaFormat.JPEG)).hasSize(255);
    }
}

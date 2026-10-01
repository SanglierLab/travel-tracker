package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.media.ThumbnailService.Dimensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailServiceTest {

    private final ThumbnailService service = new ThumbnailService();

    @TempDir
    Path dir;

    private Path jpeg(String name, int width, int height) throws IOException {
        Path file = dir.resolve(name);
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", file.toFile());
        return file;
    }

    private static int[] size(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        return new int[]{image.getWidth(), image.getHeight()};
    }

    @Test
    void reduitUneGrandePhotoSansDeformation() throws IOException {
        Path source = jpeg("big.jpg", 3000, 2000);
        Path display = dir.resolve("display.jpg");
        Path thumb = dir.resolve("thumb.jpg");

        Dimensions dimensions = service.generateDisplayAndThumb(source, display, thumb);

        assertThat(dimensions.width()).isEqualTo(1920);
        assertThat(dimensions.height()).isEqualTo(1280);
        assertThat(size(display)).containsExactly(1920, 1280);
        int[] thumbSize = size(thumb);
        assertThat(thumbSize[0]).isEqualTo(400);
        assertThat(thumbSize[1]).isBetween(266, 267);
    }

    @Test
    void neJamaisAgrandirUneImagePlusPetite() throws IOException {
        Path source = jpeg("small.jpg", 800, 600);
        Path display = dir.resolve("display.jpg");
        Path thumb = dir.resolve("thumb.jpg");

        Dimensions dimensions = service.generateDisplayAndThumb(source, display, thumb);

        assertThat(dimensions.width()).isEqualTo(800);
        assertThat(dimensions.height()).isEqualTo(600);
        assertThat(size(thumb)).containsExactly(400, 300);
    }

    @Test
    void refuseUnFichierQuiNEstPasUneImage() throws IOException {
        Path fake = dir.resolve("fake.jpg");
        Files.writeString(fake, "ceci n'est pas une image");

        assertThatThrownBy(() -> service.generateDisplayAndThumb(fake, dir.resolve("d.jpg"), dir.resolve("t.jpg")))
                .isInstanceOf(ApiException.class);
    }
}

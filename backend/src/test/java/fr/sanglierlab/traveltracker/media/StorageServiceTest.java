package fr.sanglierlab.traveltracker.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServiceTest {

    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";

    @TempDir
    Path root;

    @Test
    void organiseLesFichiersParGalerie() {
        StorageService storage = new StorageService(root);

        assertThat(storage.thumbPath(12, KEY)).isEqualTo(root.resolve("12/" + KEY + "-thumb.jpg"));
        assertThat(storage.displayPath(12, KEY)).isEqualTo(root.resolve("12/" + KEY + "-display.jpg"));
        assertThat(storage.originalPath(12, KEY, "mp4")).isEqualTo(root.resolve("12/" + KEY + "-original.mp4"));
    }

    @Test
    void refuseUneCleQuiNEstPasUnUuid() {
        StorageService storage = new StorageService(root);

        assertThatThrownBy(() -> storage.thumbPath(1, "../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nettoieLExtension() {
        StorageService storage = new StorageService(root);

        assertThat(storage.originalPath(1, KEY, "../x")).isEqualTo(root.resolve("1/" + KEY + "-original.x"));
    }

    @Test
    void supprimeLesFichiersD_unMediaEtLeDossierD_uneGalerie() throws IOException {
        StorageService storage = new StorageService(root);
        storage.prepareGalleryDir(7);
        Files.writeString(storage.thumbPath(7, KEY), "t");
        Files.writeString(storage.displayPath(7, KEY), "d");
        Files.writeString(storage.originalPath(7, KEY, "jpg"), "o");

        storage.deleteMedia(7, KEY, "jpg");
        assertThat(storage.galleryDir(7)).isEmptyDirectory();

        Files.writeString(storage.thumbPath(7, KEY), "t");
        storage.deleteGalleryDir(7);
        assertThat(storage.galleryDir(7)).doesNotExist();
        // Supprimer une galerie absente ne doit pas échouer.
        storage.deleteGalleryDir(7);
    }
}

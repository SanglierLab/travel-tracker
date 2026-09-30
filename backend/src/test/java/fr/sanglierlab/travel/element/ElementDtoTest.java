package fr.sanglierlab.travel.element;

import fr.sanglierlab.travel.element.dto.ElementDto;
import fr.sanglierlab.travel.gallery.Gallery;
import fr.sanglierlab.travel.media.StoredFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ElementDtoTest {

    private Gallery gallery() {
        return new Gallery(LocalDate.of(2026, 10, 10), "Tokyo", "Palais impérial",
                35.685, 139.752, true);
    }

    @Test
    @DisplayName("les chemins de stockage deviennent des URL publiques")
    void mapsStoragePathsToUrls() {
        StoredFiles files = new StoredFiles();
        files.storagePath = "originals/2026/10/abc.jpg";
        files.thumbPath = "thumbs/2026/10/def.jpg";
        files.mediumPath = "mediums/2026/10/ghi.jpg";
        files.mimeType = "image/jpeg";

        ElementDto dto = ElementDto.from(
                Element.media(gallery(), 0, ElementType.PHOTO, "photo.jpg", files));

        assertThat(dto.thumbUrl()).isEqualTo("/media/thumb/2026/10/def.jpg");
        assertThat(dto.mediumUrl()).isEqualTo("/media/medium/2026/10/ghi.jpg");
        assertThat(dto.originalUrl()).isEqualTo("/media/original/2026/10/abc.jpg");
        assertThat(dto.downloadUrl()).isEqualTo("/media/original/2026/10/abc.jpg?download=1");
    }

    @Test
    @DisplayName("un bloc de texte n'expose aucune URL")
    void textBlockHasNoUrls() {
        ElementDto dto = ElementDto.from(
                Element.text(gallery(), 3, "Un **beau** jardin."));

        assertThat(dto.type()).isEqualTo(ElementType.TEXT);
        assertThat(dto.markdown()).isEqualTo("Un **beau** jardin.");
        assertThat(dto.thumbUrl()).isNull();
        assertThat(dto.originalUrl()).isNull();
    }
}

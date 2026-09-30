package fr.sanglierlab.travel.gallery;

import fr.sanglierlab.travel.common.NotFoundException;
import fr.sanglierlab.travel.element.ElementService;
import fr.sanglierlab.travel.element.dto.TextBlockForm;
import fr.sanglierlab.travel.gallery.dto.GalleryDetailDto;
import fr.sanglierlab.travel.gallery.dto.GalleryForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GalleryServiceTest {

    @Autowired GalleryService galleries;
    @Autowired ElementService elements;

    private GalleryForm form(String place, String title, LocalDate date) {
        return new GalleryForm(date, place, title, 35.685, 139.752, true);
    }

    @Test
    @DisplayName("création puis relecture d'une galerie")
    void createsAndReads() {
        GalleryDetailDto created = galleries.create(
                form("Tokyo", "Palais impérial", LocalDate.of(2026, 10, 10)));

        assertThat(created.id()).isNotNull();
        assertThat(created.place()).isEqualTo("Tokyo");
        assertThat(created.elements()).isEmpty();

        assertThat(galleries.getPublished(created.id()).title()).isEqualTo("Palais impérial");
    }

    @Test
    @DisplayName("les caractères japonais sont conservés")
    void keepsJapaneseCharacters() {
        GalleryDetailDto created = galleries.create(
                form("東京", "皇居", LocalDate.of(2026, 10, 10)));

        assertThat(galleries.getPublished(created.id()).place()).isEqualTo("東京");
        assertThat(galleries.getPublished(created.id()).title()).isEqualTo("皇居");
    }

    @Test
    @DisplayName("un brouillon reste invisible du public")
    void draftIsHiddenFromPublic() {
        GalleryDetailDto draft = galleries.create(new GalleryForm(
                LocalDate.of(2026, 10, 11), "Kyoto", "Fushimi Inari",
                34.967, 135.772, false));

        assertThatThrownBy(() -> galleries.getPublished(draft.id()))
                .isInstanceOf(NotFoundException.class);

        assertThat(galleries.getForAdmin(draft.id()).published()).isFalse();
    }

    @Test
    @DisplayName("la timeline est anti-chronologique")
    void timelineIsReverseChronological() {
        galleries.create(form("Tokyo", "Jour 1", LocalDate.of(2026, 10, 10)));
        galleries.create(form("Kyoto", "Jour 2", LocalDate.of(2026, 10, 12)));
        galleries.create(form("Nara", "Jour 3", LocalDate.of(2026, 10, 14)));

        var page = galleries.listPublished(0, 10);

        assertThat(page.content()).extracting("place")
                .containsExactly("Nara", "Kyoto", "Tokyo");
    }

    @Test
    @DisplayName("les marqueurs suivent l'ordre du voyage")
    void markersAreChronological() {
        galleries.create(form("Tokyo", "Jour 1", LocalDate.of(2026, 10, 10)));
        galleries.create(form("Kyoto", "Jour 2", LocalDate.of(2026, 10, 12)));

        assertThat(galleries.markers()).extracting("place")
                .containsExactly("Tokyo", "Kyoto");
    }

    @Test
    @DisplayName("la pagination respecte la taille configurée")
    void paginationUsesConfiguredSize() {
        for (int day = 1; day <= 8; day++) {
            galleries.create(form("Étape " + day, "Titre", LocalDate.of(2026, 10, day)));
        }

        var page = galleries.listPublished(0, null);

        assertThat(page.content()).hasSize(5);      // app.page-size
        assertThat(page.totalElements()).isEqualTo(8);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.first()).isTrue();
    }

    @Test
    @DisplayName("le compte de médias ignore les blocs de texte")
    void mediaCountIgnoresTextBlocks() {
        GalleryDetailDto gallery = galleries.create(
                form("Tokyo", "Palais impérial", LocalDate.of(2026, 10, 10)));
        elements.addTextBlock(gallery.id(), new TextBlockForm("Un récit.", null));

        var summary = galleries.listPublished(0, 10).content().get(0);

        assertThat(summary.mediaCount()).isZero();
    }

    @Test
    @DisplayName("galerie inconnue : erreur explicite")
    void unknownGalleryFails() {
        assertThatThrownBy(() -> galleries.getPublished(999_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Galerie");
    }

    @Test
    @DisplayName("sans photo géolocalisée, aucune position n'est suggérée")
    void noSuggestionWithoutExif() {
        GalleryDetailDto gallery = galleries.create(
                form("Tokyo", "Palais impérial", LocalDate.of(2026, 10, 10)));

        assertThat(galleries.suggestPosition(gallery.id()).available()).isFalse();
    }
}

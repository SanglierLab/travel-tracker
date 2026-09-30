package fr.sanglierlab.travel.element;

import fr.sanglierlab.travel.common.BadRequestException;
import fr.sanglierlab.travel.element.dto.ElementDto;
import fr.sanglierlab.travel.element.dto.ElementUpdateForm;
import fr.sanglierlab.travel.element.dto.ReorderForm;
import fr.sanglierlab.travel.element.dto.TextBlockForm;
import fr.sanglierlab.travel.gallery.GalleryService;
import fr.sanglierlab.travel.gallery.dto.GalleryForm;
import org.junit.jupiter.api.BeforeEach;
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
class ElementServiceTest {

    @Autowired GalleryService galleries;
    @Autowired ElementService elements;

    private Long galleryId;

    @BeforeEach
    void createGallery() {
        galleryId = galleries.create(new GalleryForm(
                LocalDate.of(2026, 10, 10), "Tokyo", "Palais impérial",
                35.685, 139.752, true)).id();
    }

    @Test
    @DisplayName("les blocs de texte s'ajoutent à la suite")
    void appendsTextBlocks() {
        elements.addTextBlock(galleryId, new TextBlockForm("Premier", null));
        elements.addTextBlock(galleryId, new TextBlockForm("Deuxième", null));

        assertThat(elements.listByGallery(galleryId))
                .extracting(ElementDto::markdown)
                .containsExactly("Premier", "Deuxième");
    }

    @Test
    @DisplayName("un bloc peut s'insérer entre deux autres")
    void insertsAfterGivenElement() {
        ElementDto first = elements.addTextBlock(galleryId, new TextBlockForm("Premier", null));
        elements.addTextBlock(galleryId, new TextBlockForm("Dernier", null));

        elements.addTextBlock(galleryId, new TextBlockForm("Milieu", first.id()));

        assertThat(elements.listByGallery(galleryId))
                .extracting(ElementDto::markdown)
                .containsExactly("Premier", "Milieu", "Dernier");
    }

    @Test
    @DisplayName("les positions restent compactes après insertion")
    void positionsStayCompactAfterInsert() {
        ElementDto first = elements.addTextBlock(galleryId, new TextBlockForm("A", null));
        elements.addTextBlock(galleryId, new TextBlockForm("B", null));
        elements.addTextBlock(galleryId, new TextBlockForm("C", first.id()));

        assertThat(elements.listByGallery(galleryId))
                .extracting(ElementDto::position)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("réorganisation complète du flux")
    void reordersElements() {
        ElementDto a = elements.addTextBlock(galleryId, new TextBlockForm("A", null));
        ElementDto b = elements.addTextBlock(galleryId, new TextBlockForm("B", null));
        ElementDto c = elements.addTextBlock(galleryId, new TextBlockForm("C", null));

        elements.reorder(galleryId, new ReorderForm(List.of(c.id(), a.id(), b.id())));

        assertThat(elements.listByGallery(galleryId))
                .extracting(ElementDto::markdown)
                .containsExactly("C", "A", "B");
    }

    @Test
    @DisplayName("une liste de réorganisation incomplète est refusée")
    void rejectsPartialReorder() {
        ElementDto a = elements.addTextBlock(galleryId, new TextBlockForm("A", null));
        elements.addTextBlock(galleryId, new TextBlockForm("B", null));

        assertThatThrownBy(() ->
                elements.reorder(galleryId, new ReorderForm(List.of(a.id()))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rechargez la page");
    }

    @Test
    @DisplayName("la suppression renumérote les éléments restants")
    void compactsPositionsAfterDelete() {
        elements.addTextBlock(galleryId, new TextBlockForm("A", null));
        ElementDto b = elements.addTextBlock(galleryId, new TextBlockForm("B", null));
        elements.addTextBlock(galleryId, new TextBlockForm("C", null));

        elements.delete(b.id());

        assertThat(elements.listByGallery(galleryId))
                .extracting(ElementDto::position)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("modification du contenu d'un bloc de texte")
    void updatesTextContent() {
        ElementDto block = elements.addTextBlock(galleryId, new TextBlockForm("Avant", null));

        ElementDto updated = elements.update(block.id(),
                new ElementUpdateForm(null, "Après **correction**"));

        assertThat(updated.markdown()).isEqualTo("Après **correction**");
    }

    @Test
    @DisplayName("un bloc de texte ne peut pas être vidé")
    void rejectsEmptyText() {
        ElementDto block = elements.addTextBlock(galleryId, new TextBlockForm("Contenu", null));

        assertThatThrownBy(() ->
                elements.update(block.id(), new ElementUpdateForm(null, "   ")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("insérer après un élément d'une autre galerie est refusé")
    void rejectsCrossGalleryAnchor() {
        ElementDto foreign = elements.addTextBlock(galleryId, new TextBlockForm("Ailleurs", null));

        Long otherGallery = galleries.create(new GalleryForm(
                LocalDate.of(2026, 10, 11), "Kyoto", "Kinkaku-ji",
                35.039, 135.729, true)).id();

        assertThatThrownBy(() ->
                elements.addTextBlock(otherGallery, new TextBlockForm("Texte", foreign.id())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("autre galerie");
    }
}

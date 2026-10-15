package fr.sanglierlab.traveltracker.feed;

import fr.sanglierlab.traveltracker.gallery.GalleryDetail;
import fr.sanglierlab.traveltracker.gallery.GalleryService;
import fr.sanglierlab.traveltracker.gallery.GallerySummary;
import fr.sanglierlab.traveltracker.gallery.PageResponse;
import fr.sanglierlab.traveltracker.stage.StageResponse;
import fr.sanglierlab.traveltracker.stage.StageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static fr.sanglierlab.traveltracker.feed.FeedOrderTest.gallery;
import static fr.sanglierlab.traveltracker.feed.FeedOrderTest.stage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedServiceTest {

    private GalleryService galleries;
    private StageService stages;
    private FeedService feed;

    @BeforeEach
    void setUp() {
        galleries = mock(GalleryService.class);
        stages = mock(StageService.class);
        // Taille de page 3 ; « aujourd'hui » = 10 mars 2026.
        feed = new FeedService(galleries, stages, 3, Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC));

        when(galleries.summaries()).thenReturn(List.of(gallery(2, "2026-03-08"), gallery(1, "2026-03-02")));
        // L'étape 12 est à venir : absente du fil.
        when(stages.list()).thenReturn(List.of(stage(10, "2026-03-01"), stage(11, "2026-03-08"), stage(12, "2026-03-20")));
        when(galleries.publicDetails(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> new GalleryDetail(id, "g" + id, "lieu", LocalDate.parse("2026-03-01"),
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of())).toList();
        });
    }

    @Test
    void lesAlbumsEtLesEtapesCommenceesSontPagineesEnsemble() {
        PageResponse<FeedEntry> first = feed.page(1);
        PageResponse<FeedEntry> second = feed.page(2);

        // Ordre : album 2 et étape 11 (même jour, album d'abord), album 1, étape 10
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).extracting(FeedEntry::type)
                .containsExactly(FeedOrder.Type.GALLERY, FeedOrder.Type.STAGE, FeedOrder.Type.GALLERY);
        assertThat(second.content()).hasSize(1);
        assertThat(second.content().get(0).stage().id()).isEqualTo(10);
    }

    @Test
    void lesEtapesAVenirNeSontPasDansLeFil() {
        assertThat(feed.page(1).content()).noneMatch(e -> e.stage() != null && e.stage().id() == 12);
        assertThat(feed.positions().stages()).doesNotContainKey(12L);
    }

    @Test
    void unePageAuDelaDeLaDerniereEstVide() {
        assertThat(feed.page(9).content()).isEmpty();
    }

    @Test
    void laCarteEtLesPositionsIndiquentLaPageDansLeFilMelange() {
        List<GallerySummary> map = feed.map();

        assertThat(map).extracting(GallerySummary::id, GallerySummary::page).containsExactly(
                org.assertj.core.groups.Tuple.tuple(2L, 1), org.assertj.core.groups.Tuple.tuple(1L, 1));
        FeedPositions positions = feed.positions();
        assertThat(positions.today()).isEqualTo(LocalDate.parse("2026-03-10"));
        assertThat(positions.stages()).containsEntry(11L, 1).containsEntry(10L, 2);
    }
}

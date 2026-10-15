package fr.sanglierlab.traveltracker.feed;

import fr.sanglierlab.traveltracker.gallery.GallerySummary;
import fr.sanglierlab.traveltracker.stage.StageKind;
import fr.sanglierlab.traveltracker.stage.StageResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedOrderTest {

    static GallerySummary gallery(long id, String date) {
        return new GallerySummary(id, "g" + id, "lieu", LocalDate.parse(date), BigDecimal.ZERO, BigDecimal.ZERO, null, 1);
    }

    static StageResponse stage(long id, String start) {
        return new StageResponse(id, "s" + id, StageKind.PLACE, LocalDate.parse(start), LocalDate.parse(start),
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, false, 0, null);
    }

    private static List<String> ids(List<FeedOrder.Slot> slots) {
        return slots.stream().map(s -> (s.type() == FeedOrder.Type.GALLERY ? "g" : "s") + s.id()).toList();
    }

    @Test
    void melangeAlbumsEtEtapesDuPlusRecentAuPlusAncien() {
        List<FeedOrder.Slot> slots = FeedOrder.order(
                List.of(gallery(2, "2026-03-05"), gallery(1, "2026-03-01")),
                List.of(stage(10, "2026-03-03"), stage(11, "2026-03-06")));

        assertThat(ids(slots)).containsExactly("s11", "g2", "s10", "g1");
    }

    @Test
    void uneEtapeSAfficheSousLesAlbumsDuMemeJour() {
        List<FeedOrder.Slot> slots = FeedOrder.order(
                List.of(gallery(3, "2026-03-05"), gallery(2, "2026-03-05")),
                List.of(stage(10, "2026-03-05")));

        assertThat(ids(slots)).containsExactly("g3", "g2", "s10");
    }

    @Test
    void entreEtapesDuMemeJourLaPlusAvanceeDansLItineraireVientEnPremier() {
        List<FeedOrder.Slot> slots = FeedOrder.order(List.of(), List.of(stage(10, "2026-03-05"), stage(11, "2026-03-05")));

        assertThat(ids(slots)).containsExactly("s11", "s10");
    }
}

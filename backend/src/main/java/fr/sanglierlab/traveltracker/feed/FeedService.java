package fr.sanglierlab.traveltracker.feed;

import fr.sanglierlab.traveltracker.config.AppProperties;
import fr.sanglierlab.traveltracker.gallery.GalleryDetail;
import fr.sanglierlab.traveltracker.gallery.GalleryService;
import fr.sanglierlab.traveltracker.gallery.GallerySummary;
import fr.sanglierlab.traveltracker.gallery.PageResponse;
import fr.sanglierlab.traveltracker.stage.StageResponse;
import fr.sanglierlab.traveltracker.stage.StageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le fil de la partie publique : albums et étapes déjà commencées, paginés ensemble (pageSize éléments par page).
 * Les étapes à venir n'y sont pas : le front les affiche à part, dans un bloc repliable (voir {@code /api/public/itinerary}).
 */
@Service
public class FeedService {

    private final GalleryService galleries;
    private final StageService stages;
    private final int pageSize;
    private final Clock clock;

    @Autowired
    public FeedService(GalleryService galleries, StageService stages, AppProperties properties) {
        this(galleries, stages, properties.pageSize(), Clock.systemDefaultZone());
    }

    FeedService(GalleryService galleries, StageService stages, int pageSize, Clock clock) {
        this.galleries = galleries;
        this.stages = stages;
        this.pageSize = pageSize;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedEntry> page(int page) {
        List<GallerySummary> allGalleries = galleries.summaries();
        List<StageResponse> started = startedStages();
        List<FeedOrder.Slot> slots = FeedOrder.order(allGalleries, started);

        int index = Math.max(page, 1) - 1;
        int totalPages = (slots.size() + pageSize - 1) / pageSize;
        int from = (int) Math.min((long) index * pageSize, slots.size());
        int to = Math.min(from + pageSize, slots.size());
        List<FeedOrder.Slot> visible = slots.subList(from, to);

        Map<Long, GalleryDetail> details = new HashMap<>();
        List<Long> galleryIds = visible.stream().filter(s -> s.type() == FeedOrder.Type.GALLERY).map(FeedOrder.Slot::id).toList();
        for (GalleryDetail detail : galleries.publicDetails(galleryIds)) {
            details.put(detail.id(), detail);
        }
        Map<Long, StageResponse> stagesById = new HashMap<>();
        started.forEach(s -> stagesById.put(s.id(), s));

        List<FeedEntry> content = new ArrayList<>(visible.size());
        for (FeedOrder.Slot slot : visible) {
            if (slot.type() == FeedOrder.Type.GALLERY) {
                GalleryDetail detail = details.get(slot.id());
                if (detail != null) {
                    content.add(new FeedEntry(slot.type(), detail, null));
                }
            } else {
                content.add(new FeedEntry(slot.type(), null, stagesById.get(slot.id())));
            }
        }
        return new PageResponse<>(content, index + 1, pageSize, slots.size(), totalPages);
    }

    /** Marqueurs de la carte : tous les albums, avec leur page dans le fil mélangé. */
    @Transactional(readOnly = true)
    public List<GallerySummary> map() {
        List<GallerySummary> all = galleries.summaries();
        Map<Long, Integer> pages = pagesOf(FeedOrder.order(all, startedStages()), FeedOrder.Type.GALLERY);
        return all.stream().map(g -> g.withPage(pages.getOrDefault(g.id(), 1))).toList();
    }

    @Transactional(readOnly = true)
    public FeedPositions positions() {
        List<StageResponse> started = startedStages();
        List<FeedOrder.Slot> slots = FeedOrder.order(galleries.summaries(), started);
        return new FeedPositions(today(), pagesOf(slots, FeedOrder.Type.STAGE));
    }

    // -----------------------------------------------------------------------

    private Map<Long, Integer> pagesOf(List<FeedOrder.Slot> slots, FeedOrder.Type type) {
        Map<Long, Integer> pages = new HashMap<>();
        for (int rank = 0; rank < slots.size(); rank++) {
            FeedOrder.Slot slot = slots.get(rank);
            if (slot.type() == type) {
                pages.put(slot.id(), rank / pageSize + 1);
            }
        }
        return pages;
    }

    private List<StageResponse> startedStages() {
        LocalDate today = today();
        return stages.list().stream().filter(s -> !s.startDate().isAfter(today)).toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}

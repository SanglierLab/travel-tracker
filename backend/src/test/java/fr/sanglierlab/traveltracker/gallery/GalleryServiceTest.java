package fr.sanglierlab.traveltracker.gallery;

import fr.sanglierlab.traveltracker.config.AppProperties;
import fr.sanglierlab.traveltracker.media.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GalleryServiceTest {

    private static Gallery gallery(long id) {
        Gallery g = new Gallery("Titre " + id, "Lieu", LocalDate.of(2026, 10, 1),
                new BigDecimal("35.5"), new BigDecimal("139.7"));
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }

    @Test
    void indiqueLaPageDeChaqueGalerieSelonLaTailleDePage() {
        GalleryRepository galleries = mock(GalleryRepository.class);
        GalleryItemRepository items = mock(GalleryItemRepository.class);
        List<Gallery> five = new ArrayList<>();
        for (long id = 5; id >= 1; id--) {
            five.add(gallery(id));
        }
        when(galleries.findAll(any(Sort.class))).thenReturn(five);
        when(items.findCovers()).thenReturn(List.of());

        AppProperties properties = new AppProperties(List.of(), "x".repeat(24), Path.of("."),
                DataSize.ofMegabytes(30), DataSize.ofMegabytes(500), 2, 6, 5, Duration.ofMinutes(15), "ffmpeg");
        GalleryService service = new GalleryService(galleries, items,
                new ItemMapper(new MarkdownService()), mock(StorageService.class), properties);

        List<GallerySummary> summaries = service.summaries();

        assertThat(summaries).extracting(GallerySummary::id).containsExactly(5L, 4L, 3L, 2L, 1L);
        assertThat(summaries).extracting(GallerySummary::page).containsExactly(1, 1, 2, 2, 3);
    }
}

package fr.sanglierlab.travel.gallery;

import fr.sanglierlab.travel.common.NotFoundException;
import fr.sanglierlab.travel.common.PageDto;
import fr.sanglierlab.travel.config.AppProperties;
import fr.sanglierlab.travel.element.ElementRepository;
import fr.sanglierlab.travel.element.dto.ElementDto;
import fr.sanglierlab.travel.gallery.dto.GalleryDetailDto;
import fr.sanglierlab.travel.gallery.dto.GalleryForm;
import fr.sanglierlab.travel.gallery.dto.GalleryMarkerDto;
import fr.sanglierlab.travel.gallery.dto.GallerySummaryDto;
import fr.sanglierlab.travel.gallery.dto.PositionSuggestionDto;
import fr.sanglierlab.travel.media.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cycle de vie des galeries : consultation publique, administration, et
 * suppression des fichiers associés.
 */
@Service
public class GalleryService {

    private static final Logger log = LoggerFactory.getLogger(GalleryService.class);

    private final GalleryRepository galleries;
    private final ElementRepository elements;
    private final MediaService mediaService;
    private final int defaultPageSize;

    public GalleryService(GalleryRepository galleries,
                          ElementRepository elements,
                          MediaService mediaService,
                          AppProperties properties) {
        this.galleries = galleries;
        this.elements = elements;
        this.mediaService = mediaService;
        this.defaultPageSize = properties.pageSize();
    }

    // ------------------------------------------------------------- lecture

    /** Timeline publique, anti-chronologique. */
    @Transactional(readOnly = true)
    public PageDto<GallerySummaryDto> listPublished(Integer page, Integer size) {
        Pageable pageable = pageable(page, size);
        return summarize(galleries.findByPublishedTrueOrderByDateDescIdDesc(pageable));
    }

    /** Vue administrateur : brouillons compris. */
    @Transactional(readOnly = true)
    public PageDto<GallerySummaryDto> listAll(Integer page, Integer size) {
        Pageable pageable = pageable(page, size);
        return summarize(galleries.findAllByOrderByDateDescIdDesc(pageable));
    }

    @Transactional(readOnly = true)
    public List<GalleryMarkerDto> markers() {
        return galleries.findMarkers();
    }

    @Transactional(readOnly = true)
    public GalleryDetailDto getPublished(Long id) {
        Gallery gallery = galleries.findByIdAndPublishedTrue(id)
                .orElseThrow(() -> NotFoundException.of("Galerie", id));
        return detail(gallery);
    }

    @Transactional(readOnly = true)
    public GalleryDetailDto getForAdmin(Long id) {
        return detail(require(id));
    }

    // -------------------------------------------------------------- écriture

    @Transactional
    public GalleryDetailDto create(GalleryForm form) {
        Gallery gallery = new Gallery(
                form.date(), form.place().trim(), form.title().trim(),
                form.latitude(), form.longitude(), form.publishedOrDefault());

        Gallery saved = galleries.save(gallery);
        log.info("Galerie créée : {} — {}", saved.getDate(), saved.fullLabel());
        return detail(saved);
    }

    @Transactional
    public GalleryDetailDto update(Long id, GalleryForm form) {
        Gallery gallery = require(id);
        gallery.setDate(form.date());
        gallery.setPlace(form.place().trim());
        gallery.setTitle(form.title().trim());
        gallery.moveTo(form.latitude(), form.longitude());
        gallery.setPublished(form.publishedOrDefault());
        return detail(gallery);
    }

    /**
     * Suppression de la galerie et de tous ses médias.
     *
     * Les chemins sont relevés avant la suppression en base : ensuite, la
     * cascade les aurait effacés et les fichiers resteraient orphelins sur le
     * disque du NAS, invisibles et jamais récupérés.
     */
    @Transactional
    public void delete(Long id) {
        Gallery gallery = require(id);
        List<Object[]> paths = elements.findFilePathsByGalleryId(id);

        galleries.delete(gallery);   // cascade sur les éléments

        for (Object[] row : paths) {
            mediaService.deleteFiles((String) row[0], (String) row[1], (String) row[2]);
        }
        log.info("Galerie supprimée : {} — {} ({} médias)",
                gallery.getDate(), gallery.fullLabel(), paths.size());
    }

    /**
     * Position suggérée à partir des coordonnées EXIF des photos.
     *
     * Une moyenne arithmétique suffit : les clichés d'une même étape tiennent
     * dans quelques centaines de mètres, très loin des cas où la discontinuité
     * du méridien 180 poserait problème.
     */
    @Transactional(readOnly = true)
    public PositionSuggestionDto suggestPosition(Long galleryId) {
        require(galleryId);
        List<Object[]> rows = elements.averageExifPosition(galleryId);
        if (rows.isEmpty()) {
            return PositionSuggestionDto.none();
        }
        Object[] row = rows.get(0);
        Double latitude = (Double) row[0];
        Double longitude = (Double) row[1];
        long count = row[2] == null ? 0 : ((Number) row[2]).longValue();

        if (latitude == null || longitude == null || count == 0) {
            return PositionSuggestionDto.none();
        }
        return new PositionSuggestionDto(true, latitude, longitude, count);
    }

    /** Accès interne : les autres services en ont besoin comme entité. */
    @Transactional(readOnly = true)
    public Gallery require(Long id) {
        return galleries.findById(id)
                .orElseThrow(() -> NotFoundException.of("Galerie", id));
    }

    // --------------------------------------------------------------- interne

    private Pageable pageable(Integer page, Integer size) {
        int effectiveSize = size == null || size < 1 ? defaultPageSize : Math.min(size, 50);
        return PageRequest.of(page == null || page < 0 ? 0 : page, effectiveSize);
    }

    /**
     * Enrichit une page de galeries avec le nombre de médias et la vignette de
     * couverture. Deux requêtes groupées au lieu de deux par ligne.
     */
    private PageDto<GallerySummaryDto> summarize(Page<Gallery> page) {
        List<Long> ids = page.getContent().stream().map(Gallery::getId).toList();

        Map<Long, Long> counts = new HashMap<>();
        Map<Long, String> covers = new HashMap<>();

        if (!ids.isEmpty()) {
            for (Object[] row : galleries.countMediaByGalleryIds(ids)) {
                counts.put((Long) row[0], ((Number) row[1]).longValue());
            }
            for (Object[] row : galleries.findCoverThumbs(ids)) {
                covers.putIfAbsent((Long) row[0], thumbUrl((String) row[1]));
            }
        }

        return PageDto.of(page, gallery -> new GallerySummaryDto(
                gallery.getId(),
                gallery.getDate(),
                gallery.getPlace(),
                gallery.getTitle(),
                gallery.getLatitude(),
                gallery.getLongitude(),
                gallery.isPublished(),
                counts.getOrDefault(gallery.getId(), 0L),
                covers.get(gallery.getId())));
    }

    private GalleryDetailDto detail(Gallery gallery) {
        List<ElementDto> content = elements
                .findByGalleryIdOrderByPositionAsc(gallery.getId())
                .stream()
                .map(ElementDto::from)
                .toList();

        return new GalleryDetailDto(
                gallery.getId(),
                gallery.getDate(),
                gallery.getPlace(),
                gallery.getTitle(),
                gallery.getLatitude(),
                gallery.getLongitude(),
                gallery.isPublished(),
                content);
    }

    private static String thumbUrl(String storedPath) {
        if (storedPath == null) {
            return null;
        }
        int slash = storedPath.indexOf('/');
        return "/media/thumb/" + (slash < 0 ? storedPath : storedPath.substring(slash + 1));
    }
}

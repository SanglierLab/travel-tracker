package fr.sanglierlab.traveltracker.gallery;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.common.Transactions;
import fr.sanglierlab.traveltracker.config.AppProperties;
import fr.sanglierlab.traveltracker.media.MediaUrls;
import fr.sanglierlab.traveltracker.media.StorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GalleryService {

    /** Ordre d'affichage : date décroissante, et à date égale la plus récemment créée d'abord. */
    private static final Sort ORDER = Sort.by(Sort.Order.desc("galleryDate"), Sort.Order.desc("id"));

    private final GalleryRepository galleries;
    private final GalleryItemRepository items;
    private final ItemMapper mapper;
    private final StorageService storage;
    private final AppProperties properties;

    public GalleryService(GalleryRepository galleries, GalleryItemRepository items, ItemMapper mapper,
                          StorageService storage, AppProperties properties) {
        this.galleries = galleries;
        this.items = items;
        this.mapper = mapper;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional
    public GalleryDetail create(GalleryRequest request) {
        Gallery gallery = galleries.save(new Gallery(request.title(), request.placeName(), request.galleryDate(),
                request.latitude(), request.longitude()));
        return detail(gallery, List.of());
    }

    @Transactional
    public GalleryDetail update(long id, GalleryRequest request) {
        Gallery gallery = find(id);
        gallery.update(request.title(), request.placeName(), request.galleryDate(),
                request.latitude(), request.longitude());
        return detail(gallery, itemsOf(id));
    }

    /** Supprime la galerie (et ses éléments, en cascade côté base), puis ses fichiers une fois la suppression validée. */
    @Transactional
    public void delete(long id) {
        Gallery gallery = find(id);
        galleries.delete(gallery);
        Transactions.afterCommit(() -> storage.deleteGalleryDir(id));
    }

    @Transactional(readOnly = true)
    public GalleryDetail get(long id) {
        return detail(find(id), itemsOf(id));
    }

    /** Page de la partie publique (numérotée à partir de 1), avec le contenu de chaque galerie. */
    @Transactional(readOnly = true)
    public PageResponse<GalleryDetail> publicPage(int page) {
        int pageSize = properties.pageSize();
        int index = Math.max(page, 1) - 1;
        Page<Gallery> result = galleries.findAll(PageRequest.of(index, pageSize, ORDER));

        List<Long> ids = result.getContent().stream().map(Gallery::getId).toList();
        Map<Long, List<ItemDto>> itemsByGallery = ids.isEmpty() ? Map.of()
                : items.findByGalleryIdInOrderByGalleryIdAscSortOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(GalleryItem::getGalleryId, LinkedHashMap::new,
                        Collectors.mapping(mapper::toDto, Collectors.toList())));

        List<GalleryDetail> content = result.getContent().stream()
                .map(g -> detailWithDtos(g, itemsByGallery.getOrDefault(g.getId(), List.of())))
                .toList();
        return new PageResponse<>(content, index + 1, pageSize, result.getTotalElements(), result.getTotalPages());
    }

    /** Toutes les galeries, dans l'ordre d'affichage : marqueurs de la carte et liste d'administration. */
    @Transactional(readOnly = true)
    public List<GallerySummary> summaries() {
        Map<Long, String> covers = new HashMap<>();
        for (GalleryItem cover : items.findCovers()) {
            covers.putIfAbsent(cover.getGalleryId(), MediaUrls.thumb(cover.getGalleryId(), cover.getFileKey()));
        }
        return galleries.findAll(ORDER).stream()
                .map(g -> new GallerySummary(g.getId(), g.getTitle(), g.getPlaceName(), g.getGalleryDate(),
                        g.getLatitude(), g.getLongitude(), covers.get(g.getId())))
                .toList();
    }

    // -----------------------------------------------------------------------

    private Gallery find(long id) {
        return galleries.findById(id).orElseThrow(() -> ApiException.notFound("Galerie"));
    }

    private List<GalleryItem> itemsOf(long galleryId) {
        return items.findByGalleryIdOrderBySortOrderAsc(galleryId);
    }

    private GalleryDetail detail(Gallery gallery, List<GalleryItem> galleryItems) {
        return detailWithDtos(gallery, galleryItems.stream().map(mapper::toDto).toList());
    }

    private GalleryDetail detailWithDtos(Gallery g, List<ItemDto> dtos) {
        return new GalleryDetail(g.getId(), g.getTitle(), g.getPlaceName(), g.getGalleryDate(),
                g.getLatitude(), g.getLongitude(), dtos);
    }
}

package fr.sanglierlab.traveltracker.gallery;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.common.Transactions;
import fr.sanglierlab.traveltracker.media.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opérations sur les éléments d'une galerie (chaque méthode = une courte transaction). */
@Service
public class GalleryItemService {

    private final GalleryItemRepository items;
    private final GalleryRepository galleries;
    private final StorageService storage;

    public GalleryItemService(GalleryItemRepository items, GalleryRepository galleries, StorageService storage) {
        this.items = items;
        this.galleries = galleries;
        this.storage = storage;
    }

    /** Ajoute un média déjà stocké sur disque, en dernière position. */
    @Transactional
    public GalleryItem addMedia(long galleryId, ItemType type, String fileKey, String originalFilename,
                                String extension, long sizeBytes, Integer width, Integer height) {
        requireGallery(galleryId);
        int order = items.maxSortOrder(galleryId) + 1;
        return items.save(GalleryItem.media(galleryId, order, type, fileKey, originalFilename, extension,
                sizeBytes, width, height));
    }

    @Transactional
    public GalleryItem addText(long galleryId, String markdown) {
        requireGallery(galleryId);
        int order = items.maxSortOrder(galleryId) + 1;
        return items.save(GalleryItem.text(galleryId, order, markdown.strip()));
    }

    @Transactional
    public GalleryItem updateText(long itemId, String markdown) {
        GalleryItem item = find(itemId);
        if (item.getType() != ItemType.TEXT) {
            throw ApiException.badRequest("Seuls les blocs de texte peuvent être modifiés.");
        }
        item.setTextMarkdown(markdown.strip());
        return item;
    }

    /** Supprime l'élément en base, puis ses fichiers une fois la suppression validée. */
    @Transactional
    public void delete(long itemId) {
        GalleryItem item = find(itemId);
        items.delete(item);
        if (item.getType() != ItemType.TEXT) {
            long galleryId = item.getGalleryId();
            String fileKey = item.getFileKey();
            String extension = item.getExtension();
            Transactions.afterCommit(() -> storage.deleteMedia(galleryId, fileKey, extension));
        }
    }

    /** Échange l'élément avec son voisin du dessus ou du dessous. Sans effet s'il est déjà au bout. */
    @Transactional
    public void move(long itemId, Direction direction) {
        GalleryItem item = find(itemId);
        var neighbor = direction == Direction.UP
                ? items.findFirstByGalleryIdAndSortOrderLessThanOrderBySortOrderDesc(item.getGalleryId(), item.getSortOrder())
                : items.findFirstByGalleryIdAndSortOrderGreaterThanOrderBySortOrderAsc(item.getGalleryId(), item.getSortOrder());
        neighbor.ifPresent(other -> {
            int order = item.getSortOrder();
            item.setSortOrder(other.getSortOrder());
            other.setSortOrder(order);
        });
    }

    private GalleryItem find(long itemId) {
        return items.findById(itemId).orElseThrow(() -> ApiException.notFound("Élément"));
    }

    private void requireGallery(long galleryId) {
        if (!galleries.existsById(galleryId)) {
            throw ApiException.notFound("Galerie");
        }
    }
}

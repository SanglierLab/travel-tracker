package fr.sanglierlab.traveltracker.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GalleryItemRepository extends JpaRepository<GalleryItem, Long> {

    List<GalleryItem> findByGalleryIdOrderBySortOrderAsc(Long galleryId);

    /** Éléments de plusieurs galeries en une seule requête (évite une requête par galerie). */
    List<GalleryItem> findByGalleryIdInOrderByGalleryIdAscSortOrderAsc(Collection<Long> galleryIds);

    Optional<GalleryItem> findFirstByGalleryIdAndSortOrderLessThanOrderBySortOrderDesc(Long galleryId, int sortOrder);

    Optional<GalleryItem> findFirstByGalleryIdAndSortOrderGreaterThanOrderBySortOrderAsc(Long galleryId, int sortOrder);

    @Query("select coalesce(max(i.sortOrder), 0) from GalleryItem i where i.galleryId = :galleryId")
    int maxSortOrder(@Param("galleryId") Long galleryId);

    /** Premier élément affichable (avec miniature) de chaque galerie : sert de couverture sur la carte. */
    @Query("""
            select i from GalleryItem i
            where i.fileKey is not null and i.width is not null
              and i.sortOrder = (select min(i2.sortOrder) from GalleryItem i2
                                 where i2.galleryId = i.galleryId
                                   and i2.fileKey is not null and i2.width is not null)
            """)
    List<GalleryItem> findCovers();
}

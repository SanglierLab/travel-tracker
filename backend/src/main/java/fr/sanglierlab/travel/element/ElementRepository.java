package fr.sanglierlab.travel.element;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ElementRepository extends JpaRepository<Element, Long> {

    List<Element> findByGalleryIdOrderByPositionAsc(Long galleryId);

    /** -1 si la galerie est vide : le premier élément prendra donc la position 0. */
    @Query("SELECT COALESCE(MAX(e.position), -1) FROM Element e WHERE e.gallery.id = :galleryId")
    int findMaxPosition(@Param("galleryId") Long galleryId);

    @Query("SELECT e FROM Element e JOIN FETCH e.gallery WHERE e.id = :id")
    Optional<Element> findByIdWithGallery(@Param("id") Long id);

    /** Chemins des fichiers d'une galerie, relevés avant sa suppression. */
    @Query("""
            SELECT e.storagePath, e.thumbPath, e.mediumPath
            FROM Element e
            WHERE e.gallery.id = :galleryId AND e.storagePath IS NOT NULL
            """)
    List<Object[]> findFilePathsByGalleryId(@Param("galleryId") Long galleryId);

    /**
     * Position moyenne des photos géolocalisées d'une galerie.
     * Sert à proposer un emplacement quand le marqueur n'a pas encore été
     * placé à la main.
     */
    @Query("""
            SELECT AVG(e.exifLatitude), AVG(e.exifLongitude), COUNT(e)
            FROM Element e
            WHERE e.gallery.id = :galleryId
              AND e.exifLatitude IS NOT NULL
              AND e.exifLongitude IS NOT NULL
            """)
    List<Object[]> averageExifPosition(@Param("galleryId") Long galleryId);
}

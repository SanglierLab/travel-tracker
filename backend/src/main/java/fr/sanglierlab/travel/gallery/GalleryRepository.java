package fr.sanglierlab.travel.gallery;

import fr.sanglierlab.travel.gallery.dto.GalleryMarkerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GalleryRepository extends JpaRepository<Gallery, Long> {

    /**
     * Timeline publique, anti-chronologique.
     *
     * Le tri secondaire sur l'identifiant garantit un ordre stable quand
     * plusieurs galeries partagent la même date. Sans lui, la pagination peut
     * afficher deux fois la même entrée, ou en oublier une.
     */
    Page<Gallery> findByPublishedTrueOrderByDateDescIdDesc(Pageable pageable);

    /** Vue administrateur : brouillons compris. */
    Page<Gallery> findAllByOrderByDateDescIdDesc(Pageable pageable);

    Optional<Gallery> findByIdAndPublishedTrue(Long id);

    /**
     * Marqueurs de la carte. Projection directe : ni les éléments ni les
     * champs superflus ne sont chargés, juste de quoi poser une épingle.
     *
     * Tri chronologique croissant : la carte relie les étapes dans l'ordre du
     * voyage, à l'inverse de la timeline.
     */
    @Query("""
            SELECT new fr.sanglierlab.travel.gallery.dto.GalleryMarkerDto(
                       g.id, g.date, g.place, g.title, g.latitude, g.longitude)
            FROM Gallery g
            WHERE g.published = true
            ORDER BY g.date ASC, g.id ASC
            """)
    List<GalleryMarkerDto> findMarkers();

    /**
     * Nombre de médias par galerie. Une seule requête pour toute la page,
     * plutôt qu'un comptage par ligne.
     */
    @Query("""
            SELECT e.gallery.id, COUNT(e)
            FROM Element e
            WHERE e.gallery.id IN :ids
              AND e.type <> fr.sanglierlab.travel.element.ElementType.TEXT
            GROUP BY e.gallery.id
            """)
    List<Object[]> countMediaByGalleryIds(@Param("ids") List<Long> ids);

    /**
     * Vignette de couverture : le premier média de chaque galerie.
     * Même principe, une requête pour la page entière.
     */
    @Query("""
            SELECT e.gallery.id, e.thumbPath
            FROM Element e
            WHERE e.gallery.id IN :ids
              AND e.type <> fr.sanglierlab.travel.element.ElementType.TEXT
              AND e.thumbPath IS NOT NULL
              AND e.position = (SELECT MIN(inner.position) FROM Element inner
                                 WHERE inner.gallery = e.gallery
                                   AND inner.type <> fr.sanglierlab.travel.element.ElementType.TEXT
                                   AND inner.thumbPath IS NOT NULL)
            """)
    List<Object[]> findCoverThumbs(@Param("ids") List<Long> ids);
}

package fr.sanglierlab.traveltracker.gallery;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Galerie sans son contenu : marqueurs de la carte et liste d'administration. */
public record GallerySummary(
        long id,
        String title,
        String placeName,
        LocalDate galleryDate,
        BigDecimal latitude,
        BigDecimal longitude,
        /** Miniature du premier média affichable, ou null si la galerie n'en a pas. */
        String coverUrl,
        /** Numéro (à partir de 1) de la page de la partie publique où cette galerie apparaît. */
        int page) {
}

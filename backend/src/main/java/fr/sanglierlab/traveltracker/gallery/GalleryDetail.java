package fr.sanglierlab.traveltracker.gallery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Galerie avec tous ses éléments, dans l'ordre d'affichage. */
public record GalleryDetail(
        long id,
        String title,
        String placeName,
        LocalDate galleryDate,
        BigDecimal latitude,
        BigDecimal longitude,
        List<ItemDto> items) {
}

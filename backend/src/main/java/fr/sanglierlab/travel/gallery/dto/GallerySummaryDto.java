package fr.sanglierlab.travel.gallery.dto;

import java.time.LocalDate;

/** Entrée de la timeline : assez pour donner envie d'ouvrir la galerie. */
public record GallerySummaryDto(
        Long id,
        LocalDate date,
        String place,
        String title,
        double latitude,
        double longitude,
        boolean published,
        long mediaCount,
        String coverThumb
) {}

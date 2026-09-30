package fr.sanglierlab.travel.gallery.dto;

import java.time.LocalDate;

/**
 * Galerie réduite à ce qu'exige un marqueur Leaflet.
 *
 * La carte charge toutes les étapes d'un coup — il y en aura quelques dizaines,
 * pas des milliers — tandis que la timeline reste paginée.
 */
public record GalleryMarkerDto(
        Long id,
        LocalDate date,
        String place,
        String title,
        double latitude,
        double longitude
) {}

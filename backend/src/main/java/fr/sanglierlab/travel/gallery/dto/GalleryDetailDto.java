package fr.sanglierlab.travel.gallery.dto;

import fr.sanglierlab.travel.element.dto.ElementDto;

import java.time.LocalDate;
import java.util.List;

/** Galerie complète : entête et flux ordonné des éléments. */
public record GalleryDetailDto(
        Long id,
        LocalDate date,
        String place,
        String title,
        double latitude,
        double longitude,
        boolean published,
        List<ElementDto> elements
) {}

package fr.sanglierlab.travel.trace.dto;

import fr.sanglierlab.travel.trace.TraceSource;

import java.util.List;

/**
 * Tracé prêt à être dessiné : une suite de coordonnées et son style.
 *
 * Les positions sont transmises sous forme de paires {@code [latitude,
 * longitude]} plutôt que d'objets nommés. Sur plusieurs milliers de points,
 * l'économie est substantielle, et Leaflet attend précisément ce format.
 */
public record TraceSegmentDto(
        TraceSource source,
        Long tripId,
        String label,
        String color,
        List<double[]> coordinates
) {}

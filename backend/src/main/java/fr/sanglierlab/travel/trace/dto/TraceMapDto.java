package fr.sanglierlab.travel.trace.dto;

import java.util.List;

/**
 * Ensemble des tracés à afficher, accompagné de la dernière position connue
 * du voyageur — celle sur laquelle la carte se centre à l'ouverture.
 */
public record TraceMapDto(
        List<TraceSegmentDto> segments,
        TracePointDto lastKnownPosition,
        int totalPoints
) {}

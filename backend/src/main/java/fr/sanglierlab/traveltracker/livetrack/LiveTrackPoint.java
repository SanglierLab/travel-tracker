package fr.sanglierlab.traveltracker.livetrack;

import java.time.Instant;

/** Une position de la montre reçue de Garmin. */
public record LiveTrackPoint(double latitude, double longitude, Instant recordedAt) {
}

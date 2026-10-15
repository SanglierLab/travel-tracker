package fr.sanglierlab.traveltracker.cruise;

import java.time.Instant;

/** Une position de navire reçue du fournisseur AIS. */
public record AisPosition(double latitude, double longitude, Instant recordedAt) {
}

package fr.sanglierlab.traveltracker.flight;

import java.time.Instant;

/** Une position d'avion reçue du fournisseur ADS-B. */
public record AdsbPosition(double latitude, double longitude, Instant recordedAt) {
}

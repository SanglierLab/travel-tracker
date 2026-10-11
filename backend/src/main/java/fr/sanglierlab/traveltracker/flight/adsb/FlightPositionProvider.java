package fr.sanglierlab.traveltracker.flight.adsb;

import fr.sanglierlab.traveltracker.flight.AdsbPosition;

import java.util.Optional;

public interface FlightPositionProvider {

    Optional<AdsbPosition> findPosition(String callsign);
}
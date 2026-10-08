package fr.sanglierlab.traveltracker.flight;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VALEURS EN DUR, en attendant le fournisseur ADS-B : à chaque cycle, l'avion avance d'un point sur une route
 * Paris → Tokyo, puis reste à l'arrivée. Cela permet de vérifier de bout en bout le suivi : enregistrement en base,
 * tracé en pointillés sur la carte, compteur de points dans l'administration.
 */
@Component
public class PlaceholderAdsbProvider implements AdsbProvider {

    /** Route fictive : Paris-CDG → Moscou → Pékin → Tokyo-Narita. */
    private static final double[][] WAYPOINTS = {
            {49.0097, 2.5479}, {50.9, 8.6}, {52.4, 16.0}, {54.0, 25.0}, {55.8, 37.6}, {56.5, 55.0},
            {55.0, 73.0}, {51.5, 95.0}, {46.0, 112.0}, {40.1, 116.6}, {36.5, 128.0}, {35.77, 140.39}
    };

    private final AtomicInteger next = new AtomicInteger();

    @Override
    public List<AdsbPosition> fetchPositions(String flightNumber) {
        // TODO(ADS-B) : remplacer ce corps par l'appel à l'API du fournisseur choisi.
        //   - `flightNumber` est le numéro de vol (indicatif) du vol actif : à passer à l'API.
        //   - convertir la réponse en AdsbPosition (latitude, longitude en degrés ; heure du point en UTC).
        //   - renvoyer les positions qui ne sont pas encore connues (les doublons sont de toute façon ignorés).
        //   - quand le fournisseur indique que le vol est arrivé, on pourra arrêter le suivi (AdsbBatch.stop).
        double[] waypoint = WAYPOINTS[Math.min(next.getAndIncrement(), WAYPOINTS.length - 1)];
        return List.of(new AdsbPosition(waypoint[0], waypoint[1], Instant.now()));
    }
}

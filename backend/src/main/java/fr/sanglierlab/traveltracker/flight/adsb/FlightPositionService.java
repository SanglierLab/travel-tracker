package fr.sanglierlab.traveltracker.flight.adsb;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import fr.sanglierlab.traveltracker.flight.adsb.api.AdsbLolClient;
import fr.sanglierlab.traveltracker.flight.adsb.api.FlightRadar24Client;
import fr.sanglierlab.traveltracker.flight.adsb.api.PlaneFinderClient;
import fr.sanglierlab.traveltracker.flight.AdsbPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FlightPositionService {

    private static final Logger log =
            LoggerFactory.getLogger(FlightPositionService.class);

    private final List<FlightPositionProvider> providers;

    public FlightPositionService(
            AdsbLolClient adsbLolClient,
            PlaneFinderClient planeFinderClient,
            FlightRadar24Client flightRadar24Client
    ) {
        this.providers = List.of(
                adsbLolClient
        );
    }

    /**
     * Recherche une position en utilisant les providers
     * dans leur ordre de priorité.
     *
     * @param callsign callsign de l'avion
     * @return première position exploitable trouvée
     */
    public Optional<AdsbPosition> findPosition(String callsign) {
        Objects.requireNonNull(callsign, "callsign must not be null");

        for (FlightPositionProvider provider : providers) {
            try {
                Optional<AdsbPosition> position =
                        provider.findPosition(callsign);

                if (position.isPresent()) {
                    return position;
                }

                log.debug(
                        "No position found by provider {} for {}",
                        provider.getClass().getSimpleName(),
                        callsign
                );

            } catch (Exception e) {
                log.warn(
                        "Provider {} failed for callsign {}",
                        provider.getClass().getSimpleName(),
                        callsign,
                        e
                );
            }
        }

        return Optional.empty();
    }
}
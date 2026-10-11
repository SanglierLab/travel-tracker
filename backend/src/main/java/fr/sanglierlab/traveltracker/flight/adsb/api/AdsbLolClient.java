package fr.sanglierlab.traveltracker.flight.adsb.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import fr.sanglierlab.traveltracker.flight.adsb.FlightPositionProvider;
import fr.sanglierlab.traveltracker.flight.AdsbPosition;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AdsbLolClient implements FlightPositionProvider {

    private static final String BASE_URL = "https://api.adsb.lol";

    private final RestClient restClient;

    public AdsbLolClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * Recherche un avion par callsign sur ADSB.lol.
     *
     * Une position est retournée uniquement si :
     * - le callsign retourné correspond exactement au callsign demandé ;
     * - une latitude est disponible ;
     * - une longitude est disponible.
     *
     * @param callsign callsign recherché, par exemple "AFR186"
     * @return la dernière position ADS-B connue, ou Optional.empty()
     */
    @Override
    public Optional<AdsbPosition> findPosition(String callsign) {
        Objects.requireNonNull(callsign, "callsign must not be null");

        String expectedCallsign = callsign.trim();

        if (expectedCallsign.isEmpty()) {
            return Optional.empty();
        }

        AdsbLolResponse response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/callsign/{callsign}")
                        .build(expectedCallsign))
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) "
                                + "Chrome/140.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language",
                        "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                .retrieve()
                .body(AdsbLolResponse.class);

        if (response == null || response.ac() == null) {
            return Optional.empty();
        }

        return response.ac().stream()
                .filter(aircraft -> matchesCallsign(
                        aircraft,
                        expectedCallsign
                ))
                .filter(aircraft -> aircraft.lat() != null)
                .filter(aircraft -> aircraft.lon() != null)
                .map(aircraft -> new AdsbPosition(
                        aircraft.lat(),
                        aircraft.lon(),
                        calculatePositionTimestamp(response.now(), aircraft.seenPos())
                ))
                .findFirst();
    }

    private boolean matchesCallsign(
            Aircraft aircraft,
            String expectedCallsign
    ) {
        /*
         * ADSB.lol/readsb utilise le champ "flight" pour le callsign.
         *
         * On normalise les espaces car les données Mode-S peuvent
         * contenir un callsign paddé avec des espaces.
         */
        return aircraft.flight() != null
                && expectedCallsign.equalsIgnoreCase(
                aircraft.flight().trim()
        );
    }

    /**
     * "now" est exprimé en secondes depuis l'epoch.
     * "seen_pos" indique combien de secondes avant "now" la position
     * a été reçue.
     */
    private Instant calculatePositionTimestamp(
            long now,
            Double seenPos
    ) {
        if (seenPos == null) {
            return null;
        }

        double epochSeconds = now - seenPos;

        long epochSecondsPart = (long) epochSeconds;
        long nanosPart = (long) ((epochSeconds - epochSecondsPart) * 1_000_000_000);

        return Instant.ofEpochSecond(
                epochSecondsPart,
                nanosPart
        );
    }

    /*
     * DTOs correspondant au format /v2 d'ADSB.lol.
     *
     * On ne mappe volontairement que les champs nécessaires.
     */

    private record AdsbLolResponse(
            List<Aircraft> ac,
            long now,
            long ctime,
            String msg,
            long ptime,
            int total
    ) {
    }

    private record Aircraft(
            String flight,
            Double lat,
            Double lon,
            Double seenPos,
            String type,
            String hex,
            Integer nic,
            Integer rc
    ) {
    }
}
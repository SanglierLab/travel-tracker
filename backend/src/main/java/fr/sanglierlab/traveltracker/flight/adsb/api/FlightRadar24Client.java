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
public class FlightRadar24Client implements FlightPositionProvider {

    private static final String BASE_URL = "https://www.flightradar24.com";

    private final RestClient restClient;

    public FlightRadar24Client(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * Recherche un vol sur FlightRadar24 et retourne sa position s'il est
     * actuellement en vol ("live") et que le callsign correspond exactement.
     *
     * @param callsign callsign attendu, par exemple "AFR186"
     * @return la position du vol, ou Optional.empty() si aucun vol live
     *         correspondant n'est trouvé
     */
    @Override
    public Optional<AdsbPosition> findPosition(String callsign) {
        Objects.requireNonNull(callsign, "callsign must not be null");

        String expectedCallsign = callsign.trim();

        if (expectedCallsign.isEmpty()) {
            return Optional.empty();
        }

        FlightRadarResponse response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/search/web/find")
                        .queryParam("query", expectedCallsign)
                        .queryParam("limit", 1)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .body(FlightRadarResponse.class);

        if (response == null || response.results() == null) {
            return Optional.empty();
        }

        return response.results().stream()
                .filter(result -> "live".equalsIgnoreCase(result.type()))
                .filter(result -> result.detail() != null)
                .filter(result -> expectedCallsign.equalsIgnoreCase(result.detail().callsign()))
                .map(result -> {
                    FlightDetail detail = result.detail();

                    return new AdsbPosition(
                            detail.lat(),
                            detail.lon(),
                            detail.timestamp()
                    );
                })
                .findFirst();
    }

    /*
     * DTOs correspondant uniquement à la réponse FlightRadar24.
     * Les champs dont nous n'avons pas besoin sont volontairement absents.
     */

    private record FlightRadarResponse(
            List<SearchResult> results
    ) {
    }

    private record SearchResult(
            String type,
            FlightDetail detail
    ) {
    }

    private record FlightDetail(
            double lat,
            double lon,
            String callsign,
            Instant timestamp
    ) {
    }
}
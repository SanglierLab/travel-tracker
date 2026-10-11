package fr.sanglierlab.traveltracker.flight.adsb.api;


import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import fr.sanglierlab.traveltracker.flight.adsb.FlightPositionProvider;
import fr.sanglierlab.traveltracker.flight.AdsbPosition;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PlaneFinderClient implements FlightPositionProvider {

    private static final String BASE_URL = "https://planefinder.net";

    /**
     * Valeur observée sur l'endpoint /search-api/v2.
     *
     * La documentation publique actuelle de Plane Finder ne documente pas
     * cette valeur numérique. Par prudence, on considère cette source
     * comme non fiable pour déterminer la position réelle.
     */
    private static final int UNRELIABLE_DATA_SOURCE = 80;

    private final RestClient restClient;

    public PlaneFinderClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * Recherche la position live d'un avion par callsign.
     *
     * Seuls les résultats :
     * - de type "flight"
     * - dont le callsign correspond exactement
     * - possédant une latitude et une longitude
     * - dont dataSource != 80
     *
     * sont considérés.
     *
     * Les résultats "historicFlight" sont volontairement ignorés.
     *
     * @param callsign callsign recherché, par exemple "AFR186"
     * @return la position live si elle est exploitable, sinon Optional.empty()
     */
    @Override
    public Optional<AdsbPosition> findPosition(String callsign) {
        Objects.requireNonNull(callsign, "callsign must not be null");

        String expectedCallsign = callsign.trim();

        if (expectedCallsign.isEmpty()) {
            return Optional.empty();
        }

        PlaneFinderResult[] results = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search-api/v2/{callsign}")
                        .build(expectedCallsign))
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) "
                                + "Chrome/140.0.0.0 Safari/537.36")
                .header("Accept",
                        "application/json, text/plain, */*")
                .header("Accept-Language",
                        "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer",
                        "https://planefinder.net/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .retrieve()
                .body(PlaneFinderResult[].class);

        if (results == null) {
            return Optional.empty();
        }

        return Arrays.stream(results)
                .filter(result -> "flight".equalsIgnoreCase(result.type()))
                .map(PlaneFinderResult::data)
                .filter(Objects::nonNull)
                .filter(data -> matchesCallsign(
                        data,
                        expectedCallsign
                ))
                .filter(data -> data.dataSource() == null
                        || data.dataSource() != UNRELIABLE_DATA_SOURCE)
                .filter(data -> data.lat() != null)
                .filter(data -> data.lon() != null)
                .map(this::toAdsbPosition)
                .findFirst();
    }

    private boolean matchesCallsign(
            FlightData data,
            String expectedCallsign
    ) {
        return data.callsign() != null
                && expectedCallsign.equalsIgnoreCase(
                data.callsign().trim()
        );
    }

    private AdsbPosition toAdsbPosition(FlightData data) {
        Instant recordedAt = data.lastUpdateTimestamp() == null
                ? null
                : Instant.ofEpochSecond(data.lastUpdateTimestamp());

        return new AdsbPosition(
                data.lat(),
                data.lon(),
                recordedAt
        );
    }

    private record PlaneFinderResult(
            double score,
            String type,
            FlightData data
    ) {
    }

    private record FlightData(
            String adshex,
            String reg,
            String callsign,
            String flightNumber,
            String type,
            String route,
            String routeExpanded,
            Double lat,
            Double lon,
            Integer altitude,
            Integer speed,
            Integer heading,
            String airlineCode,
            Integer dataSource,
            String category,
            Integer aircraftClass,
            Long lastUpdateTimestamp
    ) {
    }
}

package fr.sanglierlab.traveltracker.flight.adsb.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.sanglierlab.traveltracker.flight.AdsbPosition;
import fr.sanglierlab.traveltracker.flight.adsb.FlightPositionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Positions ADS-B via l'API publique d'<a href="https://api.adsb.lol/docs">adsb.lol</a> (format « v2 » de readsb) :
 * {@code GET /v2/callsign/{callsign}}.
 */
@Service
public class AdsbLolClient implements FlightPositionProvider {

    static final String BASE_URL = "https://api.adsb.lol";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Sans délai de lecture, une réponse qui n'arrive jamais bloquerait le fil du batch (et donc tout le suivi). */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** Une position plus ancienne que cela n'est plus « le vol en ce moment » : elle est ignorée. */
    static final Duration MAX_POSITION_AGE = Duration.ofMinutes(10);

    /** Au-dessus de ce nombre, l'heure de l'API est en millisecondes (13 chiffres) ; en dessous, en secondes. */
    private static final double MILLISECONDS_THRESHOLD = 1e11;

    private final RestClient restClient;

    /** Utilise le RestClient.Builder de Spring Boot s'il existe, sinon un builder standard : le client ne dépend pas de l'auto-configuration. */
    @Autowired
    public AdsbLolClient(ObjectProvider<RestClient.Builder> restClientBuilder) {
        this(buildClient(restClientBuilder.getIfAvailable(RestClient::builder), BASE_URL));
    }

    /** Pour les tests. */
    AdsbLolClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildClient(RestClient.Builder builder, String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                // Un User-Agent qui dit qui on est : si le service veut nous joindre ou nous limiter, il sait à qui il a affaire.
                .defaultHeader("User-Agent", "travel-tracker/2.0 (+https://voyage.sanglierlab.fr)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Recherche un avion par indicatif (callsign) sur adsb.lol.
     *
     * <p>Une position est renvoyée seulement si l'indicatif retourné correspond exactement à celui demandé, si latitude et
     * longitude sont présentes et si la position n'est pas périmée. Si plusieurs appareils portent le même indicatif, on garde
     * le plus récemment vu. Pas de position (hors de portée des récepteurs, par exemple) : {@link Optional#empty()}.
     *
     * <p>Une erreur réseau ou HTTP lève une exception (RestClientException) : le batch la journalise et réessaie au cycle suivant.
     *
     * @param callsign indicatif recherché, par exemple « AFR186 »
     */
    @Override
    public Optional<AdsbPosition> findPosition(String callsign) {
        Objects.requireNonNull(callsign, "callsign must not be null");
        String expected = normalize(callsign);
        if (expected.isEmpty()) {
            return Optional.empty();
        }

        AdsbLolResponse response = restClient
                .get()
                .uri("/v2/callsign/{callsign}", expected)
                .retrieve()
                .body(AdsbLolResponse.class);

        return select(response, expected, Instant.now());
    }

    /** Choisit la position à retenir dans la réponse. Visible pour les tests. */
    static Optional<AdsbPosition> select(AdsbLolResponse response, String expectedCallsign, Instant now) {
        if (response == null || response.ac() == null) {
            return Optional.empty();
        }
        Instant reference = response.now() == null ? now : fromEpoch(response.now());
        return response.ac().stream()
                .filter(aircraft -> aircraft != null && aircraft.lat() != null && aircraft.lon() != null)
                .filter(aircraft -> aircraft.flight() != null && expectedCallsign.equals(normalize(aircraft.flight())))
                // le plus récemment vu d'abord (seen_pos absent : considéré comme « à l'instant »)
                .sorted(Comparator.comparingDouble(aircraft -> aircraft.seenPos() == null ? 0 : aircraft.seenPos()))
                .map(aircraft -> new AdsbPosition(aircraft.lat(), aircraft.lon(), positionTime(reference, aircraft.seenPos())))
                .filter(position -> Duration.between(position.recordedAt(), now).compareTo(MAX_POSITION_AGE) <= 0)
                .findFirst();
    }

    /**
     * Heure de la position : {@code now - seen_pos}, arrondie à la seconde. L'API donne {@code seen_pos} à 0,1 s près : sans arrondi,
     * la même position lue à deux cycles successifs pourrait différer de quelques centaines de millisecondes et être enregistrée deux fois.
     */
    static Instant positionTime(Instant reference, Double seenPos) {
        Instant time = seenPos == null ? reference : reference.minusMillis(Math.round(seenPos * 1000));
        return Instant.ofEpochSecond(Math.round(time.toEpochMilli() / 1000.0));
    }

    /** Le « now » de la réponse est en millisecondes (format v2) ; par prudence, on accepte aussi des secondes. */
    static Instant fromEpoch(double value) {
        return value > MILLISECONDS_THRESHOLD ? Instant.ofEpochMilli(Math.round(value)) : Instant.ofEpochMilli(Math.round(value * 1000));
    }

    /** Majuscules, sans espace : les indicatifs Mode S sont complétés par des espaces jusqu'à 8 caractères. */
    static String normalize(String callsign) {
        return callsign.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    // -----------------------------------------------------------------------
    // Réponse de l'API : seuls les champs utiles sont lus (le reste est ignoré).

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdsbLolResponse(List<Aircraft> ac, Double now) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Aircraft(String flight, Double lat, Double lon, @JsonProperty("seen_pos") Double seenPos) {
    }
}

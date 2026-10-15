package fr.sanglierlab.traveltracker.cruise.ais.api;

import fr.sanglierlab.traveltracker.cruise.AisPosition;
import fr.sanglierlab.traveltracker.cruise.ais.VesselPositionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Positions AIS d'un navire, par MMSI. Parallèle de {@code AdsbLolClient} (même squelette : délais réseau, normalisation de
 * l'identifiant, sélection de la position à retenir), mais <b>l'appel au fournisseur n'est pas encore écrit</b> : il se branche
 * dans {@link #fetch(String)}, repéré par un {@code TODO(AIS)}. Tant qu'il n'est pas branché, {@link #findPosition} renvoie
 * toujours {@link Optional#empty()} (et l'indique une fois dans les journaux).
 *
 * <p>Un fournisseur AIS (AISHub, aisstream.io, MarineTraffic, VesselFinder...) renvoie en général, pour un MMSI, des champs du type
 * {@code MMSI}, {@code LATITUDE}, {@code LONGITUDE}, {@code TIMESTAMP} : à convertir en {@link Vessel}, le reste de la classe
 * ne change pas.
 */
@Service
public class VesselClient implements VesselPositionProvider {

    private static final Logger log = LoggerFactory.getLogger(VesselClient.class);

    /** TODO(AIS) : adresse de base de l'API du fournisseur choisi. */
    static final String BASE_URL = "https://TODO-fournisseur-ais.example";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Sans délai de lecture, une réponse qui n'arrive jamais bloquerait le fil du batch (et donc tout le suivi). */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Une position plus ancienne que cela n'est plus « le navire en ce moment » : elle est ignorée. Large, car un navire au large
     * n'est vu que par satellite (quelques positions par jour) ; à ajuster selon le fournisseur.
     */
    static final Duration MAX_POSITION_AGE = Duration.ofHours(6);

    /** Latitude / longitude « non disponible » du protocole AIS (valeurs hors plage que les navires émettent à défaut de GPS). */
    private static final double AIS_LATITUDE_UNAVAILABLE = 91;
    private static final double AIS_LONGITUDE_UNAVAILABLE = 181;

    private final AtomicBoolean warnedNotConnected = new AtomicBoolean();

    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // utilisé par fetch() une fois le fournisseur branché
    private final RestClient restClient;

    /** Utilise le RestClient.Builder de Spring Boot s'il existe, sinon un builder standard. */
    @Autowired
    public VesselClient(ObjectProvider<RestClient.Builder> restClientBuilder) {
        this(buildClient(restClientBuilder.getIfAvailable(RestClient::builder), BASE_URL));
    }

    /** Pour les tests. */
    VesselClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildClient(RestClient.Builder builder, String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", "travel-tracker/2.0 (+https://voyage.sanglierlab.fr)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Recherche un navire par MMSI.
     *
     * <p>Une position est renvoyée seulement si le MMSI retourné correspond exactement à celui demandé, si latitude et longitude
     * sont valides (pas les valeurs « non disponible » de l'AIS) et si la position n'est pas périmée. Sinon : {@link Optional#empty()}.
     *
     * <p>Une erreur réseau ou HTTP lève une exception (RestClientException) : le batch la journalise et réessaie au cycle suivant.
     *
     * @param mmsi MMSI recherché, par exemple « 227123456 »
     */
    @Override
    public Optional<AisPosition> findPosition(String mmsi) {
        Objects.requireNonNull(mmsi, "mmsi must not be null");
        String expected = normalize(mmsi);
        if (expected.isEmpty()) {
            return Optional.empty();
        }
        return select(fetch(expected), expected, Instant.now());
    }

    /**
     * TODO(AIS) : appeler l'API du fournisseur pour ce MMSI et convertir sa réponse en {@link Vessel} (un ou plusieurs navires ;
     * {@link #select} garde le bon). Par exemple :
     * <pre>{@code
     * ProviderResponse response = restClient.get()
     *         .uri("/ais/vessel/{mmsi}", mmsi)   // chemin et paramètres (clé d'API...) selon le fournisseur
     *         .retrieve()
     *         .body(ProviderResponse.class);
     * return response.toVessels();               // MMSI, latitude, longitude, heure UTC du point
     * }</pre>
     * Les erreurs réseau ou HTTP peuvent simplement se propager : le batch les journalise et réessaie. Une clé d'API se lit dans
     * {@code AppProperties} (application.yml externe), jamais dans le code.
     *
     * @return les navires connus du fournisseur pour ce MMSI ; liste vide s'il n'en a aucun
     */
    private List<Vessel> fetch(String mmsi) {
        if (warnedNotConnected.compareAndSet(false, true)) {
            log.warn("AIS : fournisseur de données pas encore branché (VesselClient.fetch, TODO(AIS)) : aucune position ne sera récupérée");
        }
        return List.of();
    }

    /** Choisit la position à retenir parmi les navires reçus. Visible pour les tests. */
    static Optional<AisPosition> select(List<Vessel> vessels, String expectedMmsi, Instant now) {
        if (vessels == null) {
            return Optional.empty();
        }
        return vessels.stream()
                .filter(vessel -> vessel != null && vessel.mmsi() != null && expectedMmsi.equals(normalize(vessel.mmsi())))
                .filter(vessel -> vessel.latitude() != null && vessel.longitude() != null && vessel.recordedAt() != null)
                .filter(vessel -> validCoordinates(vessel.latitude(), vessel.longitude()))
                .filter(vessel -> Duration.between(vessel.recordedAt(), now).compareTo(MAX_POSITION_AGE) <= 0)
                .max(Comparator.comparing(Vessel::recordedAt)) // la plus récente
                .map(vessel -> new AisPosition(vessel.latitude(), vessel.longitude(), vessel.recordedAt()));
    }

    private static boolean validCoordinates(double latitude, double longitude) {
        return latitude != AIS_LATITUDE_UNAVAILABLE && longitude != AIS_LONGITUDE_UNAVAILABLE
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    /** Sans espace : « 227 123 456 » devient « 227123456 ». */
    static String normalize(String mmsi) {
        return mmsi.replaceAll("\\s+", "");
    }

    /**
     * Un navire tel que le fournisseur le décrit, une fois converti : c'est le format neutre entre {@link #fetch} et {@link #select}.
     *
     * @param recordedAt heure UTC de la position (pas celle de la requête)
     */
    record Vessel(String mmsi, Double latitude, Double longitude, Instant recordedAt) {
    }
}

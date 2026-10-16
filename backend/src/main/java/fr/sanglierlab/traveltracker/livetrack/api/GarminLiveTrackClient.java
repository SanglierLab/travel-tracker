package fr.sanglierlab.traveltracker.livetrack.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackException;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackGoneException;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackPoint;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackProvider;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Positions d'une session <b>Garmin LiveTrack</b>, lues comme le fait la page publique du lien de partage. Il n'existe pas d'API
 * officielle : ce client reproduit ce que fait la page, et peut donc cesser de fonctionner si Garmin la modifie.
 *
 * <p>Enchaînement (validé en conditions réelles) :
 * <ol>
 *   <li>{@code GET /session/{id}/token/{jeton}} : la page pose des cookies (dont {@code livetrack_csrf}) et contient, dans une balise
 *       {@code <meta name="…csrf…" content="…">}, un jeton CSRF ;</li>
 *   <li>{@code GET /api/sessions/{id}/track-points/common?token={jeton}&begin={heure ISO}} avec ces cookies et l'en-tête
 *       {@code livetrack-csrf-token} : seuls les points postérieurs à {@code begin} sont renvoyés (un point toutes les 10 s environ).</li>
 * </ol>
 * Sans le cookie ou sans l'en-tête, Garmin répond 403 : sur un 403 (cookie ou jeton expiré), le client recharge la page une fois et réessaie.
 *
 * <p><b>Confidentialité</b> : le jeton du lien donne la position en direct. Les erreurs de Spring citent l'adresse appelée (donc le jeton) :
 * elles sont toutes converties en {@link LiveTrackException} sans cause et sans adresse.
 */
@Service
public class GarminLiveTrackClient implements LiveTrackProvider {

    static final String BASE_URL = "https://livetrack.garmin.com";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Sans délai de lecture, une réponse qui n'arrive jamais bloquerait le fil du batch. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Au premier appel (aucun point connu), on demande les points des dernières 24 h : l'API ne renvoie que ce qui suit {@code begin}.
     * Une session plus longue perd son début si le suivi n'est démarré qu'après coup ; le suivi se démarre en général avec l'activité.
     */
    static final Duration FIRST_FETCH_LOOKBACK = Duration.ofHours(24);

    private static final String USER_AGENT = "travel-tracker/2.0 (+https://voyage.sanglierlab.fr)";

    private static final Pattern CSRF_NAME_FIRST = Pattern.compile("(?i)<meta[^>]+name=[\"']([^\"']*csrf[^\"']*)[\"'][^>]+content=[\"']([^\"']+)[\"']");
    private static final Pattern CSRF_CONTENT_FIRST = Pattern.compile("(?i)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']([^\"']*csrf[^\"']*)[\"']");

    /** Cookies et jeton CSRF obtenus en chargeant la page d'une session. */
    private record Auth(String sessionId, String token, RestClient client, String csrf) {

        boolean isFor(String sessionId, String token) {
            return this.sessionId.equals(sessionId) && this.token.equals(token);
        }

        @Override
        public String toString() {
            return "Auth[session=" + sessionId.substring(0, Math.min(8, sessionId.length())) + "…]";
        }
    }

    private final String baseUrl;
    private final Function<CookieManager, RestClient> clientFactory;

    /** Un seul suivi LiveTrack est actif à la fois : on garde l'authentification de la session en cours. */
    private volatile Auth auth;

    @Autowired
    public GarminLiveTrackClient() {
        this(BASE_URL, GarminLiveTrackClient::newClient);
    }

    /** Pour les tests : serveur local et client dédié. */
    GarminLiveTrackClient(String baseUrl, Function<CookieManager, RestClient> clientFactory) {
        this.baseUrl = baseUrl;
        this.clientFactory = clientFactory;
    }

    /** Un client par session : ses cookies lui sont propres (la page les pose, l'API les exige). */
    static RestClient newClient(CookieManager cookies) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).cookieHandler(cookies).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .requestFactory(factory)
                // Un User-Agent qui dit qui on est, comme pour adsb.lol.
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    @Override
    public LiveTrackUpdate fetch(String sessionId, String token, Instant since) {
        Instant begin = (since != null ? since : Instant.now().minus(FIRST_FETCH_LOOKBACK)).truncatedTo(ChronoUnit.MILLIS);
        Auth current = auth;
        if (current == null || !current.isFor(sessionId, token)) {
            current = authenticate(sessionId, token);
        }
        try {
            return toUpdate(requestPoints(current, begin));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 403) {
                throw translate(e);
            }
            // Cookie ou jeton CSRF expiré : on recharge la page et on réessaie une seule fois.
            current = authenticate(sessionId, token);
            try {
                return toUpdate(requestPoints(current, begin));
            } catch (RestClientException retry) {
                throw translate(retry);
            }
        } catch (RestClientException e) {
            throw translate(e);
        }
    }

    private Auth authenticate(String sessionId, String token) {
        CookieManager cookies = new CookieManager();
        RestClient client = clientFactory.apply(cookies);
        String html;
        try {
            html = client.get().uri(URI.create(baseUrl + "/session/" + sessionId + "/token/" + token))
                    .header("Accept", "text/html")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw translate(e);
        }
        String csrf = extractCsrf(html).orElseThrow(() ->
                new LiveTrackException("jeton CSRF introuvable dans la page LiveTrack : la page de Garmin a probablement changé"));
        Auth created = new Auth(sessionId, token, client, csrf);
        auth = created;
        return created;
    }

    private ApiResponse requestPoints(Auth current, Instant begin) {
        // sessionId et token ont été validés par LiveTrackLink : pas de caractère spécial à échapper, sauf l'heure.
        URI uri = URI.create(baseUrl + "/api/sessions/" + current.sessionId() + "/track-points/common?token=" + current.token()
                + "&begin=" + URLEncoder.encode(begin.toString(), StandardCharsets.UTF_8));
        return current.client().get().uri(uri)
                .header("Accept", "application/json")
                .header("livetrack-csrf-token", current.csrf())
                .retrieve()
                .body(ApiResponse.class);
    }

    /** Convertit une erreur de Spring en exception sans adresse ni jeton. */
    static LiveTrackException translate(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 404 || status == 410) {
                return new LiveTrackGoneException("la session LiveTrack n'existe plus (HTTP " + status + ")");
            }
            return new LiveTrackException("Garmin a répondu HTTP " + status);
        }
        return new LiveTrackException("Garmin injoignable (" + e.getClass().getSimpleName() + ")");
    }

    // -----------------------------------------------------------------------
    // Analyse des réponses (statique, sans réseau : testable)

    /** Jeton CSRF de la page : le contenu de la balise {@code <meta>} dont le nom contient « csrf ». */
    static Optional<String> extractCsrf(String html) {
        if (html == null) {
            return Optional.empty();
        }
        Matcher nameFirst = CSRF_NAME_FIRST.matcher(html);
        if (nameFirst.find()) {
            return Optional.of(nameFirst.group(2));
        }
        Matcher contentFirst = CSRF_CONTENT_FIRST.matcher(html);
        if (contentFirst.find()) {
            return Optional.of(contentFirst.group(1));
        }
        return Optional.empty();
    }

    /** Points valides triés par heure ; {@code ended} si un point porte l'événement END. */
    static LiveTrackUpdate toUpdate(ApiResponse response) {
        if (response == null || response.trackPoints() == null) {
            return new LiveTrackUpdate(List.of(), false);
        }
        boolean ended = false;
        List<LiveTrackPoint> points = new ArrayList<>();
        for (ApiPoint point : response.trackPoints()) {
            if (point == null) {
                continue;
            }
            if (point.eventTypes() != null && point.eventTypes().stream().anyMatch(event -> "END".equalsIgnoreCase(event))) {
                ended = true;
            }
            if (point.position() == null || point.position().lat() == null || point.position().lon() == null || point.dateTime() == null) {
                continue;
            }
            double lat = point.position().lat();
            double lon = point.position().lon();
            if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
                continue;
            }
            Instant at;
            try {
                at = Instant.parse(point.dateTime());
            } catch (DateTimeParseException e) {
                continue;
            }
            points.add(new LiveTrackPoint(lat, lon, at));
        }
        points.sort(Comparator.comparing(LiveTrackPoint::recordedAt));
        return new LiveTrackUpdate(List.copyOf(points), ended);
    }

    // -----------------------------------------------------------------------
    // Réponse de l'API : seuls les champs utiles sont lus (le reste est ignoré).

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(List<ApiPoint> trackPoints) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiPoint(String dateTime, ApiPosition position, List<String> eventTypes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiPosition(Double lat, Double lon) {
    }
}

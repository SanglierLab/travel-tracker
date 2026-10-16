package fr.sanglierlab.traveltracker.livetrack.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackException;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackGoneException;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarminLiveTrackClientTest {

    private static final String SESSION = "1debb03f-2acd-48b0-a8c9-2ba613bfcb3c";
    private static final String TOKEN = "5B72E4867D3AE6EB4B3D11F612044D8";

    private static final String JSON = """
            {"trackPoints":[
              {"dateTime":"2026-10-15T17:20:10.000Z","position":{"lat":45.10,"lon":5.20},"eventTypes":[],"speed":1.2},
              {"dateTime":"2026-10-15T17:20:00.000Z","position":{"lat":45.00,"lon":5.10},"eventTypes":[],"speed":"$undefined"}
            ]}""";

    // --- Analyse (sans réseau) ---

    @Test
    void extraitLeJetonCsrfDansLesDeuxOrdresDAttributs() {
        assertThat(GarminLiveTrackClient.extractCsrf("<head><meta name=\"csrf-token\" content=\"abc123\"></head>")).contains("abc123");
        assertThat(GarminLiveTrackClient.extractCsrf("<meta content='xyz' name='x-csrf'/>")).contains("xyz");
        assertThat(GarminLiveTrackClient.extractCsrf("<meta name=\"viewport\" content=\"width=device-width\">")).isEmpty();
        assertThat(GarminLiveTrackClient.extractCsrf(null)).isEmpty();
    }

    @Test
    void trieLesPointsEtIgnoreLesInvalides() {
        var update = GarminLiveTrackClient.toUpdate(new GarminLiveTrackClient.ApiResponse(List.of(
                new GarminLiveTrackClient.ApiPoint("2026-10-15T17:20:10.000Z", new GarminLiveTrackClient.ApiPosition(45.1, 5.2), List.of()),
                new GarminLiveTrackClient.ApiPoint("2026-10-15T17:20:00.000Z", new GarminLiveTrackClient.ApiPosition(45.0, 5.1), null),
                new GarminLiveTrackClient.ApiPoint("pas une date", new GarminLiveTrackClient.ApiPosition(45.0, 5.1), List.of()),
                new GarminLiveTrackClient.ApiPoint("2026-10-15T17:20:20.000Z", new GarminLiveTrackClient.ApiPosition(95.0, 5.1), List.of()),
                new GarminLiveTrackClient.ApiPoint("2026-10-15T17:20:30.000Z", new GarminLiveTrackClient.ApiPosition(null, 5.1), List.of()),
                new GarminLiveTrackClient.ApiPoint("2026-10-15T17:20:40.000Z", null, List.of()),
                null)));

        assertThat(update.ended()).isFalse();
        assertThat(update.points()).extracting(p -> p.recordedAt().toString())
                .containsExactly("2026-10-15T17:20:00Z", "2026-10-15T17:20:10Z");
    }

    @Test
    void detecteLaFinDeSession() {
        var update = GarminLiveTrackClient.toUpdate(new GarminLiveTrackClient.ApiResponse(List.of(
                new GarminLiveTrackClient.ApiPoint("2026-10-15T17:20:00.000Z", new GarminLiveTrackClient.ApiPosition(45.0, 5.1), List.of("PAUSE")),
                new GarminLiveTrackClient.ApiPoint("2026-10-15T17:20:10.000Z", new GarminLiveTrackClient.ApiPosition(45.0, 5.1), List.of("END")))));

        assertThat(update.ended()).isTrue();
        assertThat(update.points()).hasSize(2);
    }

    @Test
    void uneReponseVideNEstPasUneErreur() {
        assertThat(GarminLiveTrackClient.toUpdate(null).points()).isEmpty();
        assertThat(GarminLiveTrackClient.toUpdate(new GarminLiveTrackClient.ApiResponse(null)).points()).isEmpty();
        assertThat(GarminLiveTrackClient.toUpdate(new GarminLiveTrackClient.ApiResponse(List.of())).ended()).isFalse();
    }

    @Test
    void lesErreursNeRevelentNiAdresseNiJeton() {
        RestClientResponseException notFound = new RestClientResponseException(
                "404 on https://livetrack.garmin.com/api/sessions/" + SESSION + "/track-points/common?token=" + TOKEN,
                404, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        RestClientResponseException server = new RestClientResponseException("500 " + TOKEN,
                500, "Error", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        RestClientException io = new RestClientException("I/O error on GET request for \"https://x/" + TOKEN + "\"");

        assertThat(GarminLiveTrackClient.translate(notFound)).isInstanceOf(LiveTrackGoneException.class);
        assertThat(GarminLiveTrackClient.translate(server)).isNotInstanceOf(LiveTrackGoneException.class).hasMessageContaining("HTTP 500");
        for (LiveTrackException e : List.of(GarminLiveTrackClient.translate(notFound), GarminLiveTrackClient.translate(server),
                GarminLiveTrackClient.translate(io))) {
            assertThat(e.getMessage()).doesNotContain(TOKEN).doesNotContain(SESSION).doesNotContain("http");
            assertThat(e.getCause()).isNull();
        }
    }

    // --- De bout en bout, avec un faux Garmin local ---

    private HttpServer server;
    private final AtomicInteger pageHits = new AtomicInteger();
    private final List<String> apiQueries = new CopyOnWriteArrayList<>();
    /** Jeton CSRF actuellement valable ; change à chaque chargement de la page. */
    private volatile String validCsrf = "";
    private volatile int pageStatus = 200;
    private volatile boolean rotateOnNextApi;
    private volatile boolean alwaysForbidden;

    @BeforeEach
    void startFakeGarmin() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/session/" + SESSION + "/token/" + TOKEN, this::page);
        server.createContext("/api/sessions/" + SESSION + "/track-points/common", this::api);
        server.start();
    }

    @AfterEach
    void stopFakeGarmin() {
        server.stop(0);
    }

    private void page(HttpExchange exchange) throws IOException {
        pageHits.incrementAndGet();
        if (pageStatus != 200) {
            reply(exchange, pageStatus, "");
            return;
        }
        validCsrf = "csrf-" + pageHits.get();
        exchange.getResponseHeaders().add("Set-Cookie", "livetrack_csrf=cookie-" + pageHits.get() + "; Path=/");
        reply(exchange, 200, "<html><head><meta name=\"csrf-token\" content=\"" + validCsrf + "\"></head></html>");
    }

    private void api(HttpExchange exchange) throws IOException {
        apiQueries.add(exchange.getRequestURI().getRawQuery());
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        String csrf = exchange.getRequestHeaders().getFirst("livetrack-csrf-token");
        if (rotateOnNextApi) {
            rotateOnNextApi = false;
            validCsrf = "rotated";
        }
        boolean ok = !alwaysForbidden && cookie != null && cookie.contains("livetrack_csrf=") && csrf != null && csrf.equals(validCsrf);
        reply(exchange, ok ? 200 : 403, ok ? JSON : "");
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", status == 200 && body.startsWith("{") ? "application/json" : "text/html");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private GarminLiveTrackClient client() {
        return new GarminLiveTrackClient("http://127.0.0.1:" + server.getAddress().getPort(), GarminLiveTrackClient::newClient);
    }

    @Test
    void chargeLaPagePuisLesPointsAvecCookieEtJetonCsrf() {
        Instant since = Instant.parse("2026-10-15T17:19:36.001Z");

        LiveTrackUpdate update = client().fetch(SESSION, TOKEN, since);

        assertThat(update.points()).hasSize(2);
        assertThat(update.points().get(0).recordedAt()).isEqualTo(Instant.parse("2026-10-15T17:20:00Z"));
        assertThat(update.ended()).isFalse();
        assertThat(pageHits).hasValue(1);
        assertThat(apiQueries).singleElement().asString()
                .contains("token=" + TOKEN).contains("begin=2026-10-15T17%3A19%3A36.001Z");
    }

    @Test
    void reutiliseLAuthentificationEntreLesCycles() {
        GarminLiveTrackClient client = client();

        client.fetch(SESSION, TOKEN, null);
        client.fetch(SESSION, TOKEN, Instant.parse("2026-10-15T17:20:10Z"));

        assertThat(pageHits).hasValue(1);
        assertThat(apiQueries).hasSize(2);
    }

    @Test
    void sansPremierPointDemandeLes24DernieresHeures() {
        client().fetch(SESSION, TOKEN, null);

        String query = apiQueries.get(0);
        String begin = java.net.URLDecoder.decode(query.substring(query.indexOf("begin=") + 6), StandardCharsets.UTF_8);
        Instant at = Instant.parse(begin);
        assertThat(Instant.now().minus(GarminLiveTrackClient.FIRST_FETCH_LOOKBACK).minusSeconds(60)).isBefore(at);
        assertThat(at).isBefore(Instant.now().minus(GarminLiveTrackClient.FIRST_FETCH_LOOKBACK).plusSeconds(60));
    }

    @Test
    void unJetonExpireRechargeLaPageEtReessaieUneFois() {
        GarminLiveTrackClient client = client();
        client.fetch(SESSION, TOKEN, null);
        rotateOnNextApi = true;

        LiveTrackUpdate update = client.fetch(SESSION, TOKEN, Instant.parse("2026-10-15T17:20:10Z"));

        assertThat(update.points()).hasSize(2);
        assertThat(pageHits).hasValue(2);
        assertThat(apiQueries).hasSize(3);
    }

    @Test
    void unRefusPersistantDonneUneErreurSansSecret() {
        alwaysForbidden = true;

        assertThatThrownBy(() -> client().fetch(SESSION, TOKEN, null))
                .isInstanceOf(LiveTrackException.class)
                .isNotInstanceOf(LiveTrackGoneException.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageNotContaining(TOKEN);
        // Une seule nouvelle tentative, pas de boucle.
        assertThat(pageHits).hasValue(2);
        assertThat(apiQueries).hasSize(2);
    }

    @Test
    void uneSessionDisparueEstSignalee() {
        pageStatus = 404;

        assertThatThrownBy(() -> client().fetch(SESSION, TOKEN, null)).isInstanceOf(LiveTrackGoneException.class);
    }

    @Test
    void unePageSansJetonCsrfEstUneErreur() {
        server.removeContext("/session/" + SESSION + "/token/" + TOKEN);
        server.createContext("/session/" + SESSION + "/token/" + TOKEN, exchange -> reply(exchange, 200, "<html>rien</html>"));

        assertThatThrownBy(() -> client().fetch(SESSION, TOKEN, null))
                .isInstanceOf(LiveTrackException.class).hasMessageContaining("CSRF");
    }

    @Test
    void unServeurInjoignableDonneUneErreurSansAdresse() throws IOException {
        int port = server.getAddress().getPort();
        server.stop(0);
        GarminLiveTrackClient client = new GarminLiveTrackClient("http://127.0.0.1:" + port, GarminLiveTrackClient::newClient);

        assertThatThrownBy(() -> client.fetch(SESSION, TOKEN, null))
                .isInstanceOf(LiveTrackException.class).hasMessageContaining("injoignable").hasMessageNotContaining(TOKEN);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); // pour @AfterEach
        server.start();
    }
}

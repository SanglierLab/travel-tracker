package fr.sanglierlab.traveltracker.flight.adsb.api;

import fr.sanglierlab.traveltracker.flight.AdsbPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AdsbLolClientTest {

    private MockRestServiceServer server;
    private AdsbLolClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AdsbLolClient(builder.baseUrl(AdsbLolClient.BASE_URL).build());
    }

    /** Réponse au format v2 : « now » en millisecondes, « seen_pos » en secondes avant « now ». */
    private static String json(long nowMillis, String... aircraft) {
        return "{\"ac\":[" + String.join(",", aircraft) + "],\"msg\":\"No error\",\"now\":" + nowMillis
                + ",\"total\":" + aircraft.length + ",\"ctime\":" + nowMillis + ",\"ptime\":0}";
    }

    private static String aircraft(String flight, String lat, String lon, String seenPos) {
        return "{\"hex\":\"3944ee\",\"type\":\"adsb_icao\",\"flight\":\"" + flight + "\",\"alt_baro\":35000"
                + (lat == null ? "" : ",\"lat\":" + lat) + (lon == null ? "" : ",\"lon\":" + lon)
                + (seenPos == null ? "" : ",\"seen_pos\":" + seenPos) + ",\"nic\":8,\"rc\":186}";
    }

    @Test
    void lirePositionEtHeureDepuisLaReponseDeLApi() {
        long now = System.currentTimeMillis();
        server.expect(requestTo("https://api.adsb.lol/v2/callsign/AFR186"))
                .andRespond(withSuccess(json(now, aircraft("AFR186  ", "61.512", "-30.25", "2.4")), MediaType.APPLICATION_JSON));

        Optional<AdsbPosition> position = client.findPosition("AFR186");

        assertThat(position).isPresent();
        assertThat(position.get().latitude()).isEqualTo(61.512);
        assertThat(position.get().longitude()).isEqualTo(-30.25);
        // seen_pos (snake_case dans le JSON) est bien lu : l'heure est « now - 2,4 s », arrondie à la seconde
        assertThat(position.get().recordedAt()).isEqualTo(AdsbLolClient.positionTime(Instant.ofEpochMilli(now), 2.4));
        server.verify();
    }

    @Test
    void lIndicatifSaisiEstNormalisePourLaRequete() {
        server.expect(requestTo("https://api.adsb.lol/v2/callsign/AFR186"))
                .andRespond(withSuccess(json(System.currentTimeMillis()), MediaType.APPLICATION_JSON));

        assertThat(client.findPosition("  afr 186 ")).isEmpty();
        server.verify();
    }

    @Test
    void pasDePositionQuandLApiNeRenvoieAucunAvion() {
        server.expect(requestTo("https://api.adsb.lol/v2/callsign/AFR186"))
                .andRespond(withSuccess("{\"ac\":[],\"msg\":\"No error\",\"now\":1,\"total\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.findPosition("AFR186")).isEmpty();
    }

    @Test
    void pasDePositionQuandLeChampAcEstAbsent() {
        server.expect(requestTo("https://api.adsb.lol/v2/callsign/AFR186"))
                .andRespond(withSuccess("{\"msg\":\"No error\",\"now\":1}", MediaType.APPLICATION_JSON));

        assertThat(client.findPosition("AFR186")).isEmpty();
    }

    @Test
    void uneErreurHttpLeveUneException() {
        server.expect(requestTo("https://api.adsb.lol/v2/callsign/AFR186")).andRespond(withServerError());

        assertThatThrownBy(() -> client.findPosition("AFR186")).isInstanceOf(RestClientException.class);
    }

    @Test
    void unIndicatifVideNAppellePasLApi() {
        assertThat(client.findPosition("   ")).isEmpty();
        server.verify(); // aucune requête attendue, aucune reçue
    }

    // ---- choix de la position dans la réponse (sans réseau) ----

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private static AdsbLolClient.Aircraft plane(String flight, Double lat, Double lon, Double seenPos) {
        return new AdsbLolClient.Aircraft(flight, lat, lon, seenPos);
    }

    private static Optional<AdsbPosition> select(String expected, AdsbLolClient.Aircraft... aircraft) {
        return AdsbLolClient.select(new AdsbLolClient.AdsbLolResponse(List.of(aircraft), (double) NOW.toEpochMilli()), expected, NOW);
    }

    @Test
    void unAutreIndicatifEstIgnore() {
        assertThat(select("AFR186", plane("AFR1860", 1.0, 2.0, 1.0), plane("AFR18", 1.0, 2.0, 1.0))).isEmpty();
    }

    @Test
    void sansLatitudeOuLongitudeOnIgnore() {
        assertThat(select("AFR186", plane("AFR186", null, 2.0, 1.0))).isEmpty();
        assertThat(select("AFR186", plane("AFR186", 1.0, null, 1.0))).isEmpty();
    }

    @Test
    void lesEspacesEtLaCasseDeLIndicatifSontToleres() {
        assertThat(select("AFR186", plane(" afr186 ", 1.0, 2.0, 1.0))).isPresent();
    }

    @Test
    void siPlusieursAppareilsPortentLIndicatifOnGardeLePlusRecemmentVu() {
        Optional<AdsbPosition> position = select("AFR186", plane("AFR186", 10.0, 10.0, 50.0), plane("AFR186", 20.0, 20.0, 3.0));

        assertThat(position).isPresent();
        assertThat(position.get().latitude()).isEqualTo(20.0);
    }

    @Test
    void unePositionPerimeeEstIgnoree() {
        assertThat(select("AFR186", plane("AFR186", 1.0, 2.0, 11 * 60.0))).isEmpty();
        assertThat(select("AFR186", plane("AFR186", 1.0, 2.0, 9 * 60.0))).isPresent();
    }

    @Test
    void sansSeenPosLHeureEstCelleDeLaReponse() {
        Optional<AdsbPosition> position = select("AFR186", plane("AFR186", 1.0, 2.0, null));

        assertThat(position.get().recordedAt()).isEqualTo(NOW);
    }

    @Test
    void lHeureDeLaReponsePeutEtreEnSecondesOuEnMillisecondes() {
        assertThat(AdsbLolClient.fromEpoch(1_760_000_000_000.0)).isEqualTo(Instant.ofEpochSecond(1_760_000_000L));
        assertThat(AdsbLolClient.fromEpoch(1_760_000_000.5)).isEqualTo(Instant.ofEpochMilli(1_760_000_000_500L));
    }

    @Test
    void lHeureDeLaPositionEstArrondieALaSeconde() {
        // 10:00:00.000 - 2,4 s = 09:59:57.600 -> 09:59:58 ; 2,3 s ou 2,5 s donnent la même seconde (9:59:58 / 9:59:57,5 -> 58)
        assertThat(AdsbLolClient.positionTime(NOW, 2.4)).isEqualTo(Instant.parse("2026-10-12T09:59:58Z"));
        assertThat(AdsbLolClient.positionTime(NOW, 2.3)).isEqualTo(Instant.parse("2026-10-12T09:59:58Z"));
    }
}

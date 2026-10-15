package fr.sanglierlab.traveltracker.cruise.ais.api;

import fr.sanglierlab.traveltracker.cruise.AisPosition;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VesselClientTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private static VesselClient.Vessel vessel(String mmsi, Double lat, Double lon, Instant at) {
        return new VesselClient.Vessel(mmsi, lat, lon, at);
    }

    private static Optional<AisPosition> select(String expected, VesselClient.Vessel... vessels) {
        return VesselClient.select(List.of(vessels), expected, NOW);
    }

    @Test
    void renvoieLaPositionDuNavireDemande() {
        Optional<AisPosition> position = select("227123456", vessel("227123456", 43.2, 5.3, NOW.minusSeconds(120)));

        assertThat(position).contains(new AisPosition(43.2, 5.3, NOW.minusSeconds(120)));
    }

    @Test
    void unAutreMmsiEstIgnore() {
        assertThat(select("227123456", vessel("227123457", 43.2, 5.3, NOW), vessel("22712345", 43.2, 5.3, NOW))).isEmpty();
    }

    @Test
    void lesEspacesDuMmsiRecuSontTolerees() {
        assertThat(select("227123456", vessel(" 227 123 456 ", 43.2, 5.3, NOW))).isPresent();
    }

    @Test
    void sansPositionOuSansHeureOnIgnore() {
        assertThat(select("227123456", vessel("227123456", null, 5.3, NOW))).isEmpty();
        assertThat(select("227123456", vessel("227123456", 43.2, null, NOW))).isEmpty();
        assertThat(select("227123456", vessel("227123456", 43.2, 5.3, null))).isEmpty();
    }

    @Test
    void lesValeursNonDisponiblesDeLAisSontIgnorees() {
        // le protocole AIS émet 91° / 181° quand le navire n'a pas de position GPS
        assertThat(select("227123456", vessel("227123456", 91.0, 181.0, NOW))).isEmpty();
        assertThat(select("227123456", vessel("227123456", 91.0, 5.3, NOW))).isEmpty();
        assertThat(select("227123456", vessel("227123456", 43.2, 181.0, NOW))).isEmpty();
        assertThat(select("227123456", vessel("227123456", 123.0, 5.3, NOW))).isEmpty();
    }

    @Test
    void siLeFournisseurEnRenvoiePlusieursOnGardeLaPlusRecente() {
        Optional<AisPosition> position = select("227123456",
                vessel("227123456", 10.0, 10.0, NOW.minusSeconds(3600)),
                vessel("227123456", 20.0, 20.0, NOW.minusSeconds(60)));

        assertThat(position.get().latitude()).isEqualTo(20.0);
    }

    @Test
    void unePositionPerimeeEstIgnoree() {
        assertThat(select("227123456", vessel("227123456", 1.0, 2.0, NOW.minus(VesselClient.MAX_POSITION_AGE).minusSeconds(1)))).isEmpty();
        assertThat(select("227123456", vessel("227123456", 1.0, 2.0, NOW.minus(VesselClient.MAX_POSITION_AGE).plusSeconds(1)))).isPresent();
    }

    @Test
    void uneReponseVideOuAbsenteDonneAucunePosition() {
        assertThat(VesselClient.select(List.of(), "227123456", NOW)).isEmpty();
        assertThat(VesselClient.select(null, "227123456", NOW)).isEmpty();
    }

    @Test
    void tantQueLeFournisseurNEstPasBrancheOnNeRenvoieJamaisDePosition() {
        VesselClient client = new VesselClient(RestClient.builder().baseUrl(VesselClient.BASE_URL).build());

        assertThat(client.findPosition("227123456")).isEmpty(); // aucun appel réseau : fetch() est un TODO
        assertThat(client.findPosition("   ")).isEmpty();
    }

    @Test
    void normaliseLeMmsi() {
        assertThat(VesselClient.normalize(" 227 123 456 ")).isEqualTo("227123456");
    }
}

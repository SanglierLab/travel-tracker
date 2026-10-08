package fr.sanglierlab.traveltracker.flight;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderAdsbProviderTest {

    @Test
    void l_avionAvanceUnPointParCycleSurLaRouteEnDur() {
        PlaceholderAdsbProvider provider = new PlaceholderAdsbProvider();

        AdsbPosition first = provider.fetchPositions("AFR1234").get(0);
        AdsbPosition second = provider.fetchPositions("AFR1234").get(0);

        assertThat(first.latitude()).isEqualTo(49.0097); // Paris-CDG
        assertThat(first.longitude()).isEqualTo(2.5479);
        assertThat(second.longitude()).isGreaterThan(first.longitude()); // il avance vers l'est
    }

    @Test
    void ilResteAL_arriveeUneFoisLaRouteTerminee() {
        PlaceholderAdsbProvider provider = new PlaceholderAdsbProvider();
        List<AdsbPosition> all = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            all.add(provider.fetchPositions("AFR1234").get(0));
        }

        AdsbPosition last = all.get(all.size() - 1);
        assertThat(last.latitude()).isEqualTo(35.77); // Tokyo-Narita
        assertThat(last.longitude()).isEqualTo(140.39);
        assertThat(all.get(30).latitude()).isEqualTo(last.latitude());
    }

    @Test
    void chaquePositionEstValideEtHorodateeMaintenant() {
        PlaceholderAdsbProvider provider = new PlaceholderAdsbProvider();

        for (int i = 0; i < 15; i++) {
            AdsbPosition position = provider.fetchPositions("AFR1234").get(0);
            assertThat(position.latitude()).isBetween(-90.0, 90.0);
            assertThat(position.longitude()).isBetween(-180.0, 180.0);
            assertThat(Duration.between(position.recordedAt(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
        }
    }
}

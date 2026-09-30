package fr.sanglierlab.travel.trip;

import fr.sanglierlab.travel.common.BadRequestException;
import fr.sanglierlab.travel.trip.dto.TripDto;
import fr.sanglierlab.travel.trip.dto.TripForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TripServiceTest {

    @Autowired TripService trips;

    private TripForm flight(String callsign, Instant departure) {
        return new TripForm(TripType.ADSB, callsign, "Paris → Tokyo", departure, "#8e44ad");
    }

    @Test
    @DisplayName("déclaration d'un vol")
    void createsFlight() {
        TripDto created = trips.create(flight("AF274", Instant.parse("2026-10-09T13:00:00Z")));

        assertThat(created.type()).isEqualTo(TripType.ADSB);
        assertThat(created.identifier()).isEqualTo("AF274");
        assertThat(created.status()).isEqualTo(TripStatus.PLANNED);
        assertThat(created.statusLabel()).isEqualTo("Planifié");
    }

    @Test
    @DisplayName("l'indicatif est normalisé en majuscules sans espace")
    void normalizesCallsign() {
        TripDto created = trips.create(flight("af 274", Instant.parse("2026-10-09T13:00:00Z")));

        assertThat(created.identifier()).isEqualTo("AF274");
    }

    @Test
    @DisplayName("le MMSI d'un navire conserve sa casse")
    void keepsMmsiAsIs() {
        TripDto created = trips.create(new TripForm(
                TripType.AIS, "227123456", "Traversée", Instant.now(), null));

        assertThat(created.identifier()).isEqualTo("227123456");
    }

    @Test
    @DisplayName("cycle planifié, en cours, terminé")
    void followsLifecycle() {
        TripDto trip = trips.create(flight("AF274", Instant.parse("2026-10-09T13:00:00Z")));

        assertThat(trips.start(trip.id()).status()).isEqualTo(TripStatus.ACTIVE);
        assertThat(trips.finish(trip.id()).status()).isEqualTo(TripStatus.FINISHED);
        assertThat(trips.reset(trip.id()).status()).isEqualTo(TripStatus.PLANNED);
    }

    @Test
    @DisplayName("démarrer deux fois est refusé")
    void rejectsDoubleStart() {
        TripDto trip = trips.create(flight("AF274", Instant.now()));
        trips.start(trip.id());

        assertThatThrownBy(() -> trips.start(trip.id()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("déjà en cours");
    }

    @Test
    @DisplayName("les trajets dont l'heure est atteinte sont activés")
    void activatesDueTrips() {
        TripDto past = trips.create(flight("AF274",
                Instant.now().minus(10, ChronoUnit.MINUTES)));
        TripDto future = trips.create(flight("JL045",
                Instant.now().plus(2, ChronoUnit.DAYS)));

        trips.activateDueTrips();

        assertThat(trips.get(past.id()).status()).isEqualTo(TripStatus.ACTIVE);
        assertThat(trips.get(future.id()).status()).isEqualTo(TripStatus.PLANNED);
    }

    @Test
    @DisplayName("un trajet ouvert trop longtemps est clos d'office")
    void finishesStaleTrips() {
        TripDto trip = trips.create(flight("AF274",
                Instant.now().minus(3, ChronoUnit.DAYS)));
        trips.activateDueTrips();

        trips.finishStaleTrips(0);

        assertThat(trips.get(trip.id()).status()).isEqualTo(TripStatus.FINISHED);
    }

    @Test
    @DisplayName("les trajets sont listés du plus récent au plus ancien")
    void listsMostRecentFirst() {
        trips.create(flight("AF274", Instant.parse("2026-10-09T13:00:00Z")));
        trips.create(flight("JL045", Instant.parse("2026-10-25T09:00:00Z")));

        assertThat(trips.list()).extracting(TripDto::identifier)
                .containsExactly("JL045", "AF274");
    }
}

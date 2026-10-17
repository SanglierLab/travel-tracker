package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripAdminServiceTest {

    private TrackedTripRepository trips;
    private TrackPointRepository points;
    private TripAdminService service;

    @BeforeEach
    void setUp() {
        trips = mock(TrackedTripRepository.class);
        points = mock(TrackPointRepository.class);
        service = new TripAdminService(trips, points);
    }

    private TrackedTrip existing(TripType type, TripStatus status, String token) {
        TrackedTrip trip = new TrackedTrip(type, "Trajet", LocalDateTime.parse("2026-10-12T00:00:00"), "ID1", token);
        trip.setStatus(status);
        ReflectionTestUtils.setField(trip, "id", 7L);
        when(trips.findById(7L)).thenReturn(Optional.of(trip));
        return trip;
    }

    @Test
    void archiveUnTrajetTermineEtGardeSesPositions() {
        TrackedTrip trip = existing(TripType.PLANE, TripStatus.FINISHED, null);

        service.archive(7);

        assertThat(trip.isArchived()).isTrue();
        verify(trips).save(trip);
        verify(points, never()).deleteByTripId(7L);
    }

    @Test
    void archiverUneSessionLiveTrackEffaceSonJeton() {
        TrackedTrip trip = existing(TripType.LIVETRACK, TripStatus.FINISHED, "SECRET");

        service.archive(7);

        assertThat(trip.getAccessToken()).isNull();
    }

    @Test
    void archiverUnVolNeTouchePasAuJetonDesAutres() {
        TrackedTrip trip = existing(TripType.PLANE, TripStatus.PLANNED, "X");

        service.archive(7);

        assertThat(trip.getAccessToken()).isEqualTo("X");
    }

    @Test
    void refuseD_archiverUnSuiviActif() {
        TrackedTrip trip = existing(TripType.BOAT, TripStatus.ACTIVE, null);

        assertThatThrownBy(() -> service.archive(7)).isInstanceOf(ApiException.class).hasMessageContaining("arrêtez-le");
        assertThat(trip.isArchived()).isFalse();
        verify(trips, never()).save(trip);
    }

    @Test
    void restaureUnTrajetArchive() {
        TrackedTrip trip = existing(TripType.PLANE, TripStatus.FINISHED, null);
        trip.setArchived(true);

        service.unarchive(7);

        assertThat(trip.isArchived()).isFalse();
    }

    @Test
    void trajetInconnuDonne404() {
        when(trips.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(9)).isInstanceOf(ApiException.class).hasMessageContaining("introuvable");
        assertThatThrownBy(() -> service.gpx(9)).isInstanceOf(ApiException.class);
    }

    @Test
    void exporteLesPositionsEnGpx() {
        existing(TripType.PLANE, TripStatus.FINISHED, null);
        when(points.findByTripIdOrderByRecordedAtAscIdAsc(7L)).thenReturn(List.of(
                new TrackPoint(TrackSource.ADSB, 7L, new BigDecimal("35.5"), new BigDecimal("139.7"), null, LocalDateTime.parse("2026-10-12T09:30:15"))));

        TripAdminService.GpxFile file = service.gpx(7);

        assertThat(file.fileName()).isEqualTo("vol-ID1-2026-10-12.gpx");
        assertThat(file.content()).contains("<trkpt lat=\"35.5\" lon=\"139.7\">");
    }

    @Test
    void refuseD_exporterUnTrajetSansPosition() {
        existing(TripType.PLANE, TripStatus.PLANNED, null);
        when(points.findByTripIdOrderByRecordedAtAscIdAsc(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.gpx(7)).isInstanceOf(ApiException.class).hasMessageContaining("aucune position");
    }
}

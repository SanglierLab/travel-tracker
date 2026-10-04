package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.track.TrackPointService.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackPointServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    private TrackPointRepository repository;
    private TrackPointService service;

    @BeforeEach
    void setUp() {
        repository = mock(TrackPointRepository.class);
        service = new TrackPointService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void enregistreUnNouveauPointAvecLesMemesArrondisQueLaBase() {
        when(repository.existsBySourceAndRecordedAtAndLatitudeAndLongitude(any(), any(), any(), any())).thenReturn(false);

        Result result = service.ingest(TrackSource.DEVICE, new BigDecimal("35.6852123456"), new BigDecimal("139.7528"),
                Instant.parse("2026-10-04T10:00:00.123456Z"));

        assertThat(result).isEqualTo(Result.CREATED);
        ArgumentCaptor<TrackPoint> saved = ArgumentCaptor.forClass(TrackPoint.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(TrackSource.DEVICE);
        assertThat(saved.getValue().getLatitude()).isEqualByComparingTo("35.685212");
        assertThat(saved.getValue().getLatitude().scale()).isEqualTo(6);
        assertThat(saved.getValue().getLongitude()).isEqualByComparingTo("139.752800");
        // DATETIME(3) : millisecondes, sans arrondi vers le haut
        assertThat(saved.getValue().getRecordedAt()).isEqualTo(LocalDateTime.parse("2026-10-04T10:00:00.123"));
    }

    @Test
    void ignoreUnPointDejaRecu() {
        when(repository.existsBySourceAndRecordedAtAndLatitudeAndLongitude(any(), any(), any(), any())).thenReturn(true);

        Result result = service.ingest(TrackSource.DEVICE, new BigDecimal("35.6852"), new BigDecimal("139.7528"),
                Instant.parse("2026-10-04T10:00:00Z"));

        assertThat(result).isEqualTo(Result.DUPLICATE);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void laRechercheDeDoublonUtiliseLesValeursNormalisees() {
        service.ingest(TrackSource.DEVICE, new BigDecimal("35.68521"), new BigDecimal("139.75"),
                Instant.parse("2026-10-04T10:00:00.5Z"));

        verify(repository).existsBySourceAndRecordedAtAndLatitudeAndLongitude(
                TrackSource.DEVICE, LocalDateTime.parse("2026-10-04T10:00:00.500"),
                new BigDecimal("35.685210"), new BigDecimal("139.750000"));
    }

    @Test
    void deuxEnvoisSimultanesIdentiquesDonnentUnDoublonSansErreur() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_point_dedup"));

        Result result = service.ingest(TrackSource.DEVICE, new BigDecimal("1"), new BigDecimal("2"),
                Instant.parse("2026-10-04T10:00:00Z"));

        assertThat(result).isEqualTo(Result.DUPLICATE);
    }

    @Test
    void refuseUneDateDansLeFutur() {
        assertThatThrownBy(() -> service.ingest(TrackSource.DEVICE, new BigDecimal("1"), new BigDecimal("2"),
                NOW.plusSeconds(2 * 24 * 3600)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("futur");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void accepteUnLegerDecalageDHorloge() {
        Result result = service.ingest(TrackSource.DEVICE, new BigDecimal("1"), new BigDecimal("2"), NOW.plusSeconds(120));
        assertThat(result).isEqualTo(Result.CREATED);
    }
}

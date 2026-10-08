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
    private static final Instant TEN_AM = Instant.parse("2026-10-04T10:00:00Z");

    private TrackPointRepository repository;
    private TrackPointService service;

    @BeforeEach
    void setUp() {
        repository = mock(TrackPointRepository.class);
        service = new TrackPointService(repository, 100, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Result ingest(String lat, String lon, String accuracy, Instant at) {
        return service.ingest(TrackSource.DEVICE, new BigDecimal(lat), new BigDecimal(lon),
                accuracy == null ? null : new BigDecimal(accuracy), at);
    }

    @Test
    void enregistreUnNouveauPointAvecLesMemesArrondisQueLaBase() {
        when(repository.existsBySourceAndRecordedAtAndLatitudeAndLongitude(any(), any(), any(), any())).thenReturn(false);

        Result result = ingest("35.6852123456", "139.7528", "12.34", Instant.parse("2026-10-04T10:00:00.123456Z"));

        assertThat(result).isEqualTo(Result.CREATED);
        ArgumentCaptor<TrackPoint> saved = ArgumentCaptor.forClass(TrackPoint.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(TrackSource.DEVICE);
        assertThat(saved.getValue().getLatitude()).isEqualByComparingTo("35.685212");
        assertThat(saved.getValue().getLatitude().scale()).isEqualTo(6);
        assertThat(saved.getValue().getLongitude()).isEqualByComparingTo("139.752800");
        assertThat(saved.getValue().getAccuracyMeters()).isEqualByComparingTo("12.3");
        assertThat(saved.getValue().getAccuracyMeters().scale()).isEqualTo(1);
        // DATETIME(3) : millisecondes, sans arrondi vers le haut
        assertThat(saved.getValue().getRecordedAt()).isEqualTo(LocalDateTime.parse("2026-10-04T10:00:00.123"));
    }

    @Test
    void unPointSansPrecisionEstAccepteEtEnregistreSansPrecision() {
        Result result = ingest("35.6852", "139.7528", null, TEN_AM);

        assertThat(result).isEqualTo(Result.CREATED);
        ArgumentCaptor<TrackPoint> saved = ArgumentCaptor.forClass(TrackPoint.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAccuracyMeters()).isNull();
    }

    @Test
    void ignoreUnPointTropImpreciSansLEnregistrerNiChercherDeDoublon() {
        Result result = ingest("35.6852", "139.7528", "250", TEN_AM);

        assertThat(result).isEqualTo(Result.IGNORED_ACCURACY);
        assertThat(result.status()).isEqualTo("ignored");
        assertThat(result.reason()).isEqualTo("accuracy");
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).existsBySourceAndRecordedAtAndLatitudeAndLongitude(any(), any(), any(), any());
    }

    @Test
    void laLimiteDePrecisionEstInclusiveEtSeBaseSurLaValeurArrondie() {
        assertThat(ingest("35.6852", "139.7528", "100", TEN_AM)).isEqualTo(Result.CREATED);
        assertThat(ingest("35.6853", "139.7528", "100.04", TEN_AM)).isEqualTo(Result.CREATED); // arrondi à 100,0
        assertThat(ingest("35.6854", "139.7528", "100.1", TEN_AM)).isEqualTo(Result.IGNORED_ACCURACY);
    }

    @Test
    void ignoreUnePositionNulle() {
        Result result = ingest("0", "0", null, TEN_AM);

        assertThat(result).isEqualTo(Result.IGNORED_NO_FIX);
        assertThat(result.status()).isEqualTo("ignored");
        assertThat(result.reason()).isEqualTo("no-fix");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void ignoreUnPointDejaRecu() {
        when(repository.existsBySourceAndRecordedAtAndLatitudeAndLongitude(any(), any(), any(), any())).thenReturn(true);

        Result result = ingest("35.6852", "139.7528", "10", TEN_AM);

        assertThat(result).isEqualTo(Result.DUPLICATE);
        assertThat(result.status()).isEqualTo("duplicate");
        assertThat(result.reason()).isNull();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void laRechercheDeDoublonUtiliseLesValeursNormalisees() {
        ingest("35.68521", "139.75", "10", Instant.parse("2026-10-04T10:00:00.5Z"));

        verify(repository).existsBySourceAndRecordedAtAndLatitudeAndLongitude(
                TrackSource.DEVICE, LocalDateTime.parse("2026-10-04T10:00:00.500"),
                new BigDecimal("35.685210"), new BigDecimal("139.750000"));
    }

    @Test
    void deuxEnvoisSimultanesIdentiquesDonnentUnDoublonSansErreur() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_point_dedup"));

        assertThat(ingest("1", "2", "10", TEN_AM)).isEqualTo(Result.DUPLICATE);
    }

    @Test
    void refuseUneDateDansLeFutur() {
        assertThatThrownBy(() -> ingest("1", "2", null, NOW.plusSeconds(2 * 24 * 3600)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("futur");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void accepteUnLegerDecalageDHorloge() {
        assertThat(ingest("1", "2", null, NOW.plusSeconds(120))).isEqualTo(Result.CREATED);
    }

    @Test
    void unPointDeVolEstRattacheAuTrajetEtUnPointDuTelephoneNe_l_estPas() {
        service.ingest(TrackSource.ADSB, 5L, new BigDecimal("49.0097"), new BigDecimal("2.5479"), null, TEN_AM);
        service.ingest(TrackSource.DEVICE, new BigDecimal("35.6852"), new BigDecimal("139.7528"), null, TEN_AM);

        ArgumentCaptor<TrackPoint> saved = ArgumentCaptor.forClass(TrackPoint.class);
        verify(repository, org.mockito.Mockito.times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().get(0).getSource()).isEqualTo(TrackSource.ADSB);
        assertThat(saved.getAllValues().get(0).getTripId()).isEqualTo(5L);
        assertThat(saved.getAllValues().get(1).getSource()).isEqualTo(TrackSource.DEVICE);
        assertThat(saved.getAllValues().get(1).getTripId()).isNull();
    }
}

package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackQueryServiceTest {

    @Test
    void laReponseIndiqueLeDelaiAvantRetardDeChaqueSource() {
        TrackPointRepository repository = mock(TrackPointRepository.class);
        when(repository.findAllRows()).thenReturn(List.of());
        AppProperties properties = new AppProperties(List.of(), "x".repeat(24), Path.of("."),
                DataSize.ofMegabytes(30), DataSize.ofMegabytes(500), 10, 6, 100, 5, Duration.ofMinutes(15), "ffmpeg", 7,
                new AppProperties.LateAfter(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(1200)));

        TrackResponse response = new TrackQueryService(repository, properties).publicTrack();

        assertThat(response.lateAfterSeconds())
                .containsEntry(TrackSource.DEVICE, 600L)
                .containsEntry(TrackSource.ADSB, 300L)
                .containsEntry(TrackSource.AIS, 1200L);
    }

    @Test
    void chaqueSourceADonneSonDelai() {
        AppProperties.LateAfter late = new AppProperties.LateAfter(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(3));

        assertThat(late.of(TrackSource.DEVICE)).isEqualTo(Duration.ofMinutes(1));
        assertThat(late.of(TrackSource.ADSB)).isEqualTo(Duration.ofMinutes(2));
        assertThat(late.of(TrackSource.AIS)).isEqualTo(Duration.ofMinutes(3));
    }
}

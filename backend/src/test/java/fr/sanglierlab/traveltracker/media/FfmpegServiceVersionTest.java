package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegServiceVersionTest {

    private static FfmpegService with(String path) {
        return new FfmpegService(new AppProperties(List.of(), "x".repeat(24), Path.of("."), DataSize.ofMegabytes(30), DataSize.ofMegabytes(500),
                10, 6, 100, 5, Duration.ofMinutes(15), path, 7,
                new AppProperties.LateAfter(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(20), Duration.ofMinutes(5))));
    }

    @Test
    void unExecutableIntrouvableDonneVide() {
        assertThat(with("/chemin/qui/n/existe/pas/ffmpeg").version()).isEmpty();
    }
}

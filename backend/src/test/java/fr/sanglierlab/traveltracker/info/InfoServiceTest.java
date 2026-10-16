package fr.sanglierlab.traveltracker.info;

import fr.sanglierlab.traveltracker.config.AppProperties;
import fr.sanglierlab.traveltracker.cruise.AisBatch;
import fr.sanglierlab.traveltracker.flight.AdsbBatch;
import fr.sanglierlab.traveltracker.gallery.GalleryItemRepository;
import fr.sanglierlab.traveltracker.gallery.GalleryRepository;
import fr.sanglierlab.traveltracker.gallery.ItemType;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackBatch;
import fr.sanglierlab.traveltracker.media.FfmpegService;
import fr.sanglierlab.traveltracker.media.StorageService;
import fr.sanglierlab.traveltracker.track.SourceStats;
import fr.sanglierlab.traveltracker.track.TrackPointRepository;
import fr.sanglierlab.traveltracker.track.TrackSource;
import fr.sanglierlab.traveltracker.track.TrackedTrip;
import fr.sanglierlab.traveltracker.track.TrackedTripRepository;
import fr.sanglierlab.traveltracker.track.TripStatus;
import fr.sanglierlab.traveltracker.track.TripType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfoServiceTest {

    @TempDir
    Path dir;

    private GalleryRepository galleries;
    private GalleryItemRepository items;
    private TrackPointRepository points;
    private TrackedTripRepository trips;
    private StorageService storage;
    private FfmpegService ffmpeg;
    private ObjectProvider<BuildProperties> build;
    private InfoService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        galleries = mock(GalleryRepository.class);
        items = mock(GalleryItemRepository.class);
        points = mock(TrackPointRepository.class);
        trips = mock(TrackedTripRepository.class);
        storage = mock(StorageService.class);
        ffmpeg = mock(FfmpegService.class);
        build = mock(ObjectProvider.class);
        AdsbBatch adsb = mock(AdsbBatch.class);
        AisBatch ais = mock(AisBatch.class);
        LiveTrackBatch liveTrack = mock(LiveTrackBatch.class);
        when(adsb.interval()).thenReturn(Duration.ofSeconds(30));
        when(ais.interval()).thenReturn(Duration.ofMinutes(5));
        when(liveTrack.interval()).thenReturn(Duration.ofSeconds(60));
        when(storage.getMediaRoot()).thenReturn(dir);
        when(points.sourceStats()).thenReturn(List.of());
        when(trips.findFirstByTypeAndStatus(any(), any())).thenReturn(Optional.empty());
        when(ffmpeg.version()).thenReturn(Optional.empty());
        AppProperties properties = new AppProperties(List.of(), "secret-token-secret-token-1234", dir,
                DataSize.ofMegabytes(30), DataSize.ofMegabytes(500), 10, 6, 100, 5, Duration.ofMinutes(15), "ffmpeg", 7,
                new AppProperties.LateAfter(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(20), Duration.ofMinutes(5)));
        service = new InfoService(properties, build, galleries, items, points, trips, storage, ffmpeg, adsb, ais, liveTrack);
    }

    @Test
    void rapporteLesLimitesEtLesDelais() {
        InfoResponse info = service.info();

        assertThat(info.limits().maxPhotoBytes()).isEqualTo(30L * 1024 * 1024);
        assertThat(info.limits().maxVideoBytes()).isEqualTo(500L * 1024 * 1024);
        assertThat(info.limits().pageSize()).isEqualTo(10);
        assertThat(info.sources()).extracting(InfoResponse.Source::source)
                .containsExactly(TrackSource.DEVICE, TrackSource.ADSB, TrackSource.AIS, TrackSource.GARMIN);
        assertThat(info.sources()).extracting(InfoResponse.Source::pollSeconds).containsExactly(null, 30L, 300L, 60L);
        assertThat(info.sources()).extracting(InfoResponse.Source::lateAfterSeconds).containsExactly(600L, 300L, 1200L, 300L);
        assertThat(info.sources()).extracting(InfoResponse.Source::trackable).containsExactly(false, true, true, true);
    }

    @Test
    void sansBuildInfoLaVersionEstCelleDeDeveloppement() {
        when(build.getIfAvailable()).thenReturn(null);

        InfoResponse.Version version = service.info().version();

        assertThat(version.version()).isEqualTo("développement");
        assertThat(version.builtAt()).isNull();
    }

    @Test
    void avecBuildInfoLaVersionEtLaDateSontCellesDuBuild() {
        Properties values = new Properties();
        values.setProperty("name", "travel-tracker");
        values.setProperty("version", "2.0.0");
        values.setProperty("time", "2026-10-15T08:00:00Z");
        when(build.getIfAvailable()).thenReturn(new BuildProperties(values));

        InfoResponse.Version version = service.info().version();

        assertThat(version.version()).isEqualTo("2.0.0");
        assertThat(version.builtAt()).isEqualTo(Instant.parse("2026-10-15T08:00:00Z"));
    }

    @Test
    void rapporteLePointCountEtLeSuiviEnCoursParSource() {
        when(points.sourceStats()).thenReturn(List.of(
                new SourceStats(TrackSource.DEVICE, 120L, LocalDateTime.parse("2026-10-15T10:00:00")),
                new SourceStats(TrackSource.GARMIN, 40L, LocalDateTime.parse("2026-10-15T11:30:00"))));
        TrackedTrip active = mock(TrackedTrip.class);
        when(active.getLabel()).thenReturn("Rando du col");
        when(trips.findFirstByTypeAndStatus(TripType.LIVETRACK, TripStatus.ACTIVE)).thenReturn(Optional.of(active));

        List<InfoResponse.Source> sources = service.info().sources();

        InfoResponse.Source device = sources.get(0);
        assertThat(device.pointCount()).isEqualTo(120);
        assertThat(device.lastPointAt()).isEqualTo(Instant.parse("2026-10-15T10:00:00Z"));
        assertThat(device.activeLabel()).isNull();
        InfoResponse.Source adsb = sources.get(1);
        assertThat(adsb.pointCount()).isZero();
        assertThat(adsb.lastPointAt()).isNull();
        InfoResponse.Source garmin = sources.get(3);
        assertThat(garmin.pointCount()).isEqualTo(40);
        assertThat(garmin.activeLabel()).isEqualTo("Rando du col");
    }

    @Test
    void rapporteLeContenuEtLEspaceDisque() {
        when(galleries.count()).thenReturn(12L);
        when(items.countByType(ItemType.PHOTO)).thenReturn(300L);
        when(items.countByType(ItemType.VIDEO)).thenReturn(8L);
        when(items.countByType(ItemType.TEXT)).thenReturn(20L);
        when(items.totalSizeBytes()).thenReturn(2_000_000_000L);

        InfoResponse.Content content = service.info().content();

        assertThat(content.galleries()).isEqualTo(12);
        assertThat(content.photos()).isEqualTo(300);
        assertThat(content.videos()).isEqualTo(8);
        assertThat(content.texts()).isEqualTo(20);
        assertThat(content.originalsBytes()).isEqualTo(2_000_000_000L);
        assertThat(content.diskFreeBytes()).isNotNull().isPositive();
        assertThat(content.diskTotalBytes()).isNotNull().isGreaterThanOrEqualTo(content.diskFreeBytes());
    }

    @Test
    void unDossierDeMediasAbsentInterrogeLeDossierExistantAuDessus() {
        Path missing = dir.resolve("pas/encore/cree");
        when(storage.getMediaRoot()).thenReturn(missing);

        assertThat(InfoService.existingParent(missing)).isEqualTo(dir);
        assertThat(service.info().content().diskFreeBytes()).isNotNull();
    }

    @Test
    void ffmpegEtApiDePositions() {
        when(ffmpeg.version()).thenReturn(Optional.of("6.1.1"));

        InfoResponse.Tools tools = service.info().tools();

        assertThat(tools.ffmpegAvailable()).isTrue();
        assertThat(tools.ffmpegVersion()).isEqualTo("6.1.1");
        assertThat(tools.positionsPath()).isEqualTo("/api/track/points");
        assertThat(tools.tokenHeader()).isEqualTo("X-API-Token");
    }

    @Test
    void ffmpegAbsentEstSignaleSansErreur() {
        assertThat(service.info().tools().ffmpegAvailable()).isFalse();
    }

    @Test
    void laReponseNeContientAucunSecret() {
        String text = service.info().toString();

        assertThat(text).doesNotContain("secret-token");
    }
}

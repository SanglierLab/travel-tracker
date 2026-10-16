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
import fr.sanglierlab.traveltracker.track.TrackedTripRepository;
import fr.sanglierlab.traveltracker.track.TripStatus;
import fr.sanglierlab.traveltracker.track.TripType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Informations de la page Config : version, limites, état des suivis, chiffres du contenu, outils. Lecture seule, sans secret. */
@Service
public class InfoService {

    /** Chemin de l'API de positions (voir TrackController) et en-tête du token : rappelés dans la page, jamais le token lui-même. */
    static final String POSITIONS_PATH = "/api/track/points";
    static final String TOKEN_HEADER = "X-API-Token";

    private final AppProperties properties;
    private final ObjectProvider<BuildProperties> build;
    private final GalleryRepository galleries;
    private final GalleryItemRepository items;
    private final TrackPointRepository points;
    private final TrackedTripRepository trips;
    private final StorageService storage;
    private final FfmpegService ffmpeg;
    private final AdsbBatch adsb;
    private final AisBatch ais;
    private final LiveTrackBatch liveTrack;

    public InfoService(AppProperties properties, ObjectProvider<BuildProperties> build, GalleryRepository galleries,
                       GalleryItemRepository items, TrackPointRepository points, TrackedTripRepository trips,
                       StorageService storage, FfmpegService ffmpeg, AdsbBatch adsb, AisBatch ais, LiveTrackBatch liveTrack) {
        this.properties = properties;
        this.build = build;
        this.galleries = galleries;
        this.items = items;
        this.points = points;
        this.trips = trips;
        this.storage = storage;
        this.ffmpeg = ffmpeg;
        this.adsb = adsb;
        this.ais = ais;
        this.liveTrack = liveTrack;
    }

    @Transactional(readOnly = true)
    public InfoResponse info() {
        return new InfoResponse(version(), limits(), sources(), content(), tools());
    }

    private InfoResponse.Version version() {
        BuildProperties info = build.getIfAvailable();
        return info == null
                ? new InfoResponse.Version("travel-tracker", "développement", null)
                : new InfoResponse.Version(info.getName() == null ? "travel-tracker" : info.getName(), info.getVersion(), info.getTime());
    }

    private InfoResponse.Limits limits() {
        return new InfoResponse.Limits(properties.maxPhotoSize().toBytes(), properties.maxVideoSize().toBytes(), properties.pageSize(),
                properties.upcomingDays(), properties.trackGapHours(), properties.trackMaxAccuracyMeters());
    }

    private List<InfoResponse.Source> sources() {
        Map<TrackSource, SourceStats> stats = points.sourceStats().stream()
                .collect(Collectors.toMap(SourceStats::source, Function.identity()));
        return List.of(
                source(TrackSource.DEVICE, null, null, stats),
                source(TrackSource.ADSB, adsb.interval().toSeconds(), TripType.PLANE, stats),
                source(TrackSource.AIS, ais.interval().toSeconds(), TripType.BOAT, stats),
                source(TrackSource.GARMIN, liveTrack.interval().toSeconds(), TripType.LIVETRACK, stats));
    }

    private InfoResponse.Source source(TrackSource source, Long pollSeconds, TripType type, Map<TrackSource, SourceStats> stats) {
        String active = type == null ? null : trips.findFirstByTypeAndStatus(type, TripStatus.ACTIVE).map(trip -> trip.getLabel()).orElse(null);
        SourceStats stat = stats.get(source);
        return new InfoResponse.Source(source, pollSeconds, properties.trackLateAfter().of(source).toSeconds(), type != null, active,
                stat == null ? 0 : stat.count(), stat == null ? null : stat.lastRecordedAt().toInstant(ZoneOffset.UTC));
    }

    private InfoResponse.Content content() {
        Long free = null;
        Long total = null;
        try {
            var store = Files.getFileStore(existingParent(storage.getMediaRoot()));
            free = store.getUsableSpace();
            total = store.getTotalSpace();
        } catch (IOException | RuntimeException e) {
            // Disque illisible : les deux valeurs restent absentes, le reste de la page s'affiche.
        }
        return new InfoResponse.Content(galleries.count(), items.countByType(ItemType.PHOTO), items.countByType(ItemType.VIDEO),
                items.countByType(ItemType.TEXT), items.totalSizeBytes(), free, total);
    }

    /** Le dossier des médias peut ne pas exister encore (aucun envoi) : on interroge alors le premier dossier existant au-dessus. */
    static Path existingParent(Path path) {
        Path current = path;
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        return current == null ? path : current;
    }

    private InfoResponse.Tools tools() {
        var version = ffmpeg.version();
        return new InfoResponse.Tools(version.isPresent(), version.orElse(null), POSITIONS_PATH, TOKEN_HEADER);
    }
}

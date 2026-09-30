package fr.sanglierlab.travel.trace;

import fr.sanglierlab.travel.common.BadRequestException;
import fr.sanglierlab.travel.common.NotFoundException;
import fr.sanglierlab.travel.common.PageDto;
import fr.sanglierlab.travel.trace.dto.IngestPointForm;
import fr.sanglierlab.travel.trace.dto.IngestResultDto;
import fr.sanglierlab.travel.trace.dto.TraceMapDto;
import fr.sanglierlab.travel.trace.dto.TracePointDto;
import fr.sanglierlab.travel.trace.dto.TracePointUpdateForm;
import fr.sanglierlab.travel.trace.dto.TraceSegmentDto;
import fr.sanglierlab.travel.trip.Trip;
import fr.sanglierlab.travel.trip.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réception, restitution et correction des positions.
 */
@Service
public class TraceService {

    private static final Logger log = LoggerFactory.getLogger(TraceService.class);

    /**
     * Au-delà de cette interruption, le tracé est coupé plutôt que prolongé
     * par une droite. Sans quoi une pause d'une nuit relierait l'hôtel du soir
     * à celui du lendemain par un trait qui ne correspond à aucun déplacement.
     */
    private static final long SEGMENT_BREAK_MINUTES = 90;

    private final TracePointRepository points;
    private final TripRepository trips;

    public TraceService(TracePointRepository points, TripRepository trips) {
        this.points = points;
        this.trips = trips;
    }

    // -------------------------------------------------------------- réception

    /**
     * Enregistre un lot de positions.
     *
     * Les doublons sont écartés sans erreur : le traceur mobile accumule ses
     * relevés hors réseau et réémet volontiers deux fois le même tampon. Un
     * point aberrant n'interrompt pas le traitement des suivants.
     */
    @Transactional
    public IngestResultDto ingest(List<IngestPointForm> forms) {
        if (forms == null || forms.isEmpty()) {
            throw new BadRequestException("Aucune position reçue");
        }

        int stored = 0;
        int duplicates = 0;
        int rejected = 0;

        for (IngestPointForm form : forms) {
            try {
                TraceSource source = form.sourceOrDefault();
                Instant measuredAt = form.measuredAtOrNow();
                Trip trip = resolveTrip(form.tripId());

                if (isDuplicate(source, measuredAt, trip)) {
                    duplicates++;
                    continue;
                }

                TracePoint point = new TracePoint(source, trip, measuredAt,
                        form.latitude(), form.longitude());
                point.setAltitudeM(form.altitudeM());
                point.setSpeedKmh(form.speedKmh());
                point.setHeadingDeg(form.headingDeg());
                point.setAccuracyM(form.accuracyM());

                points.save(point);
                stored++;

            } catch (RuntimeException e) {
                log.warn("Position écartée : {}", e.getMessage());
                rejected++;
            }
        }

        if (stored > 0) {
            log.debug("{} position(s) enregistrée(s), {} doublon(s), {} rejet(s)",
                    stored, duplicates, rejected);
        }
        return new IngestResultDto(forms.size(), stored, duplicates, rejected);
    }

    // ------------------------------------------------------------ restitution

    /**
     * Tracés prêts à être dessinés.
     *
     * Les positions du téléphone sont découpées en segments dès qu'une
     * interruption dépasse le seuil ; celles des vols et traversées forment un
     * segment par trajet, avec sa couleur propre.
     */
    @Transactional(readOnly = true)
    public TraceMapDto buildMap(TraceSource source, Instant from, Instant to) {
        List<TracePoint> selection = points.findForMap(source, from, to);

        List<TraceSegmentDto> segments = new ArrayList<>();
        segments.addAll(buildDeviceSegments(selection));
        segments.addAll(buildTripSegments(selection));

        TracePoint latest = points.findLatest(TraceSource.DEVICE);

        return new TraceMapDto(
                segments,
                latest == null ? null : TracePointDto.from(latest),
                selection.size());
    }

    @Transactional(readOnly = true)
    public List<TracePointDto> listByTrip(Long tripId) {
        return points.findByTripIdOrderByMeasuredAtAsc(tripId).stream()
                .map(TracePointDto::from)
                .toList();
    }

    // --------------------------------------------------------- administration

    @Transactional(readOnly = true)
    public PageDto<TracePointDto> listForAdmin(TraceSource source, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? 50 : Math.min(size, 200));

        var result = source == null
                ? points.findAllByOrderByMeasuredAtDesc(pageable)
                : points.findBySourceOrderByMeasuredAtDesc(source, pageable);

        return PageDto.of(result, TracePointDto::from);
    }

    @Transactional(readOnly = true)
    public TracePointDto get(Long id) {
        return TracePointDto.from(require(id));
    }

    /** Repositionne un relevé erroné plutôt que de créer un trou dans le tracé. */
    @Transactional
    public TracePointDto update(Long id, TracePointUpdateForm form) {
        TracePoint point = require(id);
        point.moveTo(form.latitude(), form.longitude());
        if (form.measuredAt() != null) {
            point.setMeasuredAt(form.measuredAt());
        }
        log.info("Position {} corrigée : {}, {}", id, form.latitude(), form.longitude());
        return TracePointDto.from(point);
    }

    @Transactional
    public void delete(Long id) {
        points.delete(require(id));
        log.info("Position {} supprimée", id);
    }

    /** Suppression groupée, pour nettoyer une série de relevés erratiques. */
    @Transactional
    public int deleteAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("Aucune position sélectionnée");
        }
        points.deleteAllById(ids);
        log.info("{} position(s) supprimée(s)", ids.size());
        return ids.size();
    }

    // ---------------------------------------------------------------- interne

    private TracePoint require(Long id) {
        return points.findById(id)
                .orElseThrow(() -> NotFoundException.of("Position", id));
    }

    private Trip resolveTrip(Long tripId) {
        if (tripId == null) {
            return null;
        }
        return trips.findById(tripId)
                .orElseThrow(() -> NotFoundException.of("Trajet", tripId));
    }

    private boolean isDuplicate(TraceSource source, Instant measuredAt, Trip trip) {
        return trip == null
                ? points.existsBySourceAndMeasuredAtAndTripIsNull(source, measuredAt)
                : points.existsBySourceAndMeasuredAtAndTripId(source, measuredAt, trip.getId());
    }

    /** Découpe la trace du téléphone en segments séparés par les longues pauses. */
    private List<TraceSegmentDto> buildDeviceSegments(List<TracePoint> selection) {
        List<TraceSegmentDto> segments = new ArrayList<>();
        List<double[]> current = new ArrayList<>();
        Instant previous = null;

        for (TracePoint point : selection) {
            if (point.getSource() != TraceSource.DEVICE) {
                continue;
            }
            boolean gap = previous != null
                    && point.getMeasuredAt().isAfter(previous.plusSeconds(SEGMENT_BREAK_MINUTES * 60));

            if (gap && current.size() > 1) {
                segments.add(deviceSegment(current));
                current = new ArrayList<>();
            } else if (gap) {
                current.clear();
            }

            current.add(new double[]{point.getLatitude(), point.getLongitude()});
            previous = point.getMeasuredAt();
        }

        if (current.size() > 1) {
            segments.add(deviceSegment(current));
        }
        return segments;
    }

    /** Un segment par trajet suivi, avec sa couleur si elle a été choisie. */
    private List<TraceSegmentDto> buildTripSegments(List<TracePoint> selection) {
        Map<Long, List<double[]>> byTrip = new LinkedHashMap<>();
        Map<Long, TracePoint> reference = new LinkedHashMap<>();

        for (TracePoint point : selection) {
            if (point.getTrip() == null) {
                continue;
            }
            Long tripId = point.getTrip().getId();
            byTrip.computeIfAbsent(tripId, key -> new ArrayList<>())
                  .add(new double[]{point.getLatitude(), point.getLongitude()});
            reference.putIfAbsent(tripId, point);
        }

        List<TraceSegmentDto> segments = new ArrayList<>();
        byTrip.forEach((tripId, coordinates) -> {
            if (coordinates.size() < 2) {
                return;
            }
            Trip trip = reference.get(tripId).getTrip();
            segments.add(new TraceSegmentDto(
                    trip.getType().traceSource(),
                    tripId,
                    trip.displayLabel(),
                    trip.getColor(),
                    coordinates));
        });
        return segments;
    }

    private TraceSegmentDto deviceSegment(List<double[]> coordinates) {
        return new TraceSegmentDto(TraceSource.DEVICE, null, null, null, List.copyOf(coordinates));
    }
}

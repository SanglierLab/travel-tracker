package fr.sanglierlab.traveltracker.stage;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.gallery.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Étapes du voyage : lieux où l'on reste et trajets (train, avion, bateau), à venir ou passés.
 * L'ordre manuel (sort_order) est celui du tracé fléché chronologique ; « trier par date » le recale sur les dates.
 */
@Service
public class StageService {

    /** Taille maximale d'un fichier GPX envoyé. */
    static final long MAX_GPX_BYTES = 10L * 1024 * 1024;

    private final StageRepository stages;

    public StageService(StageRepository stages) {
        this.stages = stages;
    }

    /** Liste d'administration, dans l'ordre manuel, sans les tracés (inutiles ici et volumineux). */
    @Transactional(readOnly = true)
    public List<StageResponse> list() {
        return stages.findAllByOrderBySortOrderAscIdAsc().stream().map(stage -> toResponse(stage, false)).toList();
    }

    /** Itinéraire de la partie publique : toutes les étapes, y compris à venir, avec leur tracé. */
    @Transactional(readOnly = true)
    public List<StageResponse> publicItinerary() {
        return stages.findAllByOrderBySortOrderAscIdAsc().stream().map(stage -> toResponse(stage, true)).toList();
    }

    @Transactional(readOnly = true)
    public StageResponse get(long id) {
        return toResponse(find(id), true);
    }

    @Transactional
    public StageResponse create(StageRequest request) {
        Stage stage = new Stage(stages.maxSortOrder() + 1);
        fill(stage, request);
        return toResponse(stages.save(stage), false);
    }

    @Transactional
    public StageResponse update(long id, StageRequest request) {
        Stage stage = find(id);
        fill(stage, request);
        if (!stage.getKind().isTrip()) {
            stage.setPathData(null); // une étape simple n'a pas de tracé
        }
        return toResponse(stages.save(stage), true);
    }

    @Transactional
    public void delete(long id) {
        stages.delete(find(id));
    }

    /** Échange l'étape avec sa voisine du dessus ou du dessous (sans effet aux extrémités). */
    @Transactional
    public void move(long id, Direction direction) {
        List<Stage> ordered = new java.util.ArrayList<>(stages.findAllByOrderBySortOrderAscIdAsc());
        int index = indexOf(ordered, id);
        int target = index + (direction == Direction.UP ? -1 : 1);
        if (target >= 0 && target < ordered.size()) {
            Stage moved = ordered.set(index, ordered.get(target));
            ordered.set(target, moved);
        }
        renumber(ordered); // renumérote tout : l'ordre se « répare » de lui-même
    }

    /** Recale l'ordre sur les dates (début, puis fin) ; à dates égales, l'ordre actuel est conservé. */
    @Transactional
    public List<StageResponse> sortByDate() {
        List<Stage> ordered = new java.util.ArrayList<>(stages.findAllByOrderBySortOrderAscIdAsc());
        ordered.sort(Comparator.comparing(Stage::getStartDate).thenComparing(Stage::getEndDate));
        renumber(ordered);
        return ordered.stream().map(stage -> toResponse(stage, false)).toList();
    }

    // --- GPX ---

    /**
     * Enregistre le tracé d'un trajet à partir d'un fichier GPX.
     *
     * @param useEndpoints si vrai, les positions de début et de fin deviennent les extrémités du tracé
     */
    public StageResponse uploadGpx(long id, InputStream gpx, long size, boolean useEndpoints) {
        Stage stage = find(id);
        if (!stage.getKind().isTrip()) {
            throw ApiException.badRequest("Seuls les trajets (train, avion, bateau) ont un tracé GPX.");
        }
        if (size > MAX_GPX_BYTES) {
            throw ApiException.payloadTooLarge("Fichier GPX trop volumineux (10 Mo maximum).");
        }
        GpxParser.Result result;
        try {
            result = GpxParser.parse(gpx);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        stage.setPathData(PathCodec.encode(result.points()));
        if (useEndpoints) {
            double[] first = result.points().get(0);
            double[] last = result.points().get(result.points().size() - 1);
            stage.setStart(BigDecimal.valueOf(first[0]), BigDecimal.valueOf(wrapLongitude(first[1])));
            stage.setEnd(BigDecimal.valueOf(last[0]), BigDecimal.valueOf(wrapLongitude(last[1])));
        }
        return toResponse(stages.save(stage), true);
    }

    @Transactional
    public StageResponse removeGpx(long id) {
        Stage stage = find(id);
        stage.setPathData(null);
        return toResponse(stages.save(stage), true);
    }

    // -----------------------------------------------------------------------

    private Stage find(long id) {
        return stages.findById(id).orElseThrow(() -> ApiException.notFound("Étape"));
    }

    private static int indexOf(List<Stage> ordered, long id) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId() == id) {
                return i;
            }
        }
        throw ApiException.notFound("Étape");
    }

    private void renumber(List<Stage> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setSortOrder(i + 1);
        }
        stages.saveAll(ordered);
    }

    private static void fill(Stage stage, StageRequest request) {
        String name = request.name().strip();
        if (name.isEmpty()) {
            throw ApiException.badRequest("Le nom est obligatoire.");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw ApiException.badRequest("La date de fin ne peut pas précéder la date de début.");
        }
        BigDecimal endLatitude = null;
        BigDecimal endLongitude = null;
        if (request.kind().isTrip()) {
            if (request.endLatitude() == null || request.endLongitude() == null) {
                throw ApiException.badRequest("Un trajet a besoin d'une position de début et d'une position de fin.");
            }
            endLatitude = request.endLatitude();
            endLongitude = request.endLongitude();
        } // une étape simple n'a qu'une position : une éventuelle position de fin est ignorée
        stage.apply(name, request.kind(), request.startDate(), request.endDate(),
                request.startLatitude(), request.startLongitude(), endLatitude, endLongitude);
    }

    /** Ramène une longitude « continue » (au-delà de ±180) dans [-180, 180[. */
    static double wrapLongitude(double longitude) {
        return ((longitude + 180) % 360 + 360) % 360 - 180;
    }

    private static StageResponse toResponse(Stage stage, boolean includePath) {
        String data = stage.getPathData();
        boolean hasPath = data != null && !data.isBlank();
        int points = hasPath ? (int) data.chars().filter(c -> c == ';').count() + 1 : 0;
        return new StageResponse(stage.getId(), stage.getName(), stage.getKind(), stage.getStartDate(), stage.getEndDate(),
                stage.getStartLatitude(), stage.getStartLongitude(), stage.getEndLatitude(), stage.getEndLongitude(),
                hasPath, points, includePath && hasPath ? PathCodec.decode(data) : null);
    }
}

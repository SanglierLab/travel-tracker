package fr.sanglierlab.traveltracker.stage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Une étape telle que vue par le front.
 *
 * @param endLatitude  position de fin ; null pour une étape simple
 * @param hasPath      un tracé GPX est enregistré (trajets seulement)
 * @param pathPoints   nombre de points du tracé enregistré
 * @param path         le tracé, points [latitude, longitude] (longitude continue : elle peut dépasser ±180) ;
 *                     null dans les listes d'administration, qui n'en ont pas besoin
 */
public record StageResponse(long id, String name, StageKind kind, LocalDate startDate, LocalDate endDate,
                            BigDecimal startLatitude, BigDecimal startLongitude,
                            BigDecimal endLatitude, BigDecimal endLongitude,
                            boolean hasPath, int pathPoints, List<double[]> path) {
}

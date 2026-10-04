package fr.sanglierlab.traveltracker.track;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathSimplifierTest {

    /** Environ 111 m par millième de degré de latitude. */
    private static final double M = 1.0 / 111_000.0;

    private static List<double[]> line(int count) {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new double[]{35.0 + i * 0.0001, 139.0});
        }
        return points;
    }

    @Test
    void uneLigneDroiteSeReduitAuxDeuxExtremites() {
        List<double[]> result = PathSimplifier.simplify(line(500), 25);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly(35.0, 139.0);
        assertThat(result.get(1)[0]).isEqualTo(35.0 + 499 * 0.0001);
    }

    @Test
    void ungCoudeEstConserve() {
        List<double[]> path = new ArrayList<>();
        for (int i = 0; i <= 50; i++) path.add(new double[]{35.0, 139.0 + i * 0.0001});          // vers l'est (~900 m)
        for (int i = 1; i <= 50; i++) path.add(new double[]{35.0 + i * 0.0001, 139.005});         // puis vers le nord

        List<double[]> result = PathSimplifier.simplify(path, 25);

        assertThat(result).hasSize(3);
        assertThat(result.get(1)).containsExactly(35.0, 139.005);
    }

    @Test
    void respecteLaTolerance() {
        // Un écart de 10 m par rapport à la ligne est lissé avec 25 m de tolérance, conservé avec 5 m.
        List<double[]> path = List.of(new double[]{35.0, 139.0}, new double[]{35.0 + 500 * M, 139.0 + 10 * M / Math.cos(Math.toRadians(35))},
                new double[]{35.0 + 1000 * M, 139.0});

        assertThat(PathSimplifier.simplify(path, 25)).hasSize(2);
        assertThat(PathSimplifier.simplify(path, 5)).hasSize(3);
    }

    @Test
    void deuxPointsOuMoinsSontRendusTelsQuels() {
        assertThat(PathSimplifier.simplify(List.of(), 25)).isEmpty();
        assertThat(PathSimplifier.simplify(List.of(new double[]{1, 2}), 25)).hasSize(1);
        assertThat(PathSimplifier.simplify(List.of(new double[]{1, 2}, new double[]{3, 4}), 25)).hasSize(2);
    }

    @Test
    void thinSupprimeLeTremblementDuGpsALArret() {
        List<double[]> still = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            still.add(new double[]{35.0 + (i % 3) * M, 139.0 + (i % 2) * M}); // quelques mètres de bruit
        }
        still.add(new double[]{35.0 + 500 * M, 139.0}); // puis un vrai déplacement de 500 m

        List<double[]> result = PathSimplifier.thin(still, 5);

        assertThat(result.size()).isLessThan(10);
        assertThat(result.get(result.size() - 1)).isEqualTo(still.get(still.size() - 1)); // le dernier point est toujours gardé
        assertThat(result.get(0)).isEqualTo(still.get(0));
    }

    @Test
    void laDistanceEstEnMetres() {
        // 0,001° de latitude ≈ 111 m
        assertThat(PathSimplifier.distanceMeters(new double[]{35.0, 139.0}, new double[]{35.001, 139.0})).isBetween(110.0, 112.0);
    }
}

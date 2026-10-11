package fr.sanglierlab.traveltracker.stage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PathCodecTest {

    @Test
    void encodeEtDecodeSansPerte() {
        List<double[]> points = List.of(new double[]{35.685212, 139.752812}, new double[]{-33.8688, 151.2093}, new double[]{10.0, 180.5});

        List<double[]> decoded = PathCodec.decode(PathCodec.encode(points));

        assertThat(decoded).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(decoded.get(i)).containsExactly(points.get(i));
        }
    }

    @Test
    void arrondiASixDecimales() {
        assertThat(PathCodec.encode(List.of(new double[]{35.12345678, 139.1}))).isEqualTo("35.123457,139.100000");
    }

    @Test
    void leFormatNeDependPasDeLaLangueDuServeur() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE); // virgule décimale
            assertThat(PathCodec.encode(List.of(new double[]{1.5, 2.5}))).isEqualTo("1.500000,2.500000");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void untexteVideOuNulDonneUnTraceVide() {
        assertThat(PathCodec.decode(null)).isEmpty();
        assertThat(PathCodec.decode("")).isEmpty();
        assertThat(PathCodec.decode("   ")).isEmpty();
        assertThat(PathCodec.encode(List.of())).isEmpty();
    }
}

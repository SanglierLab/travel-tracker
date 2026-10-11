package fr.sanglierlab.traveltracker.stage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpxParserTest {

    private static String gpx(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"test\" "
                + "xmlns=\"http://www.topografix.com/GPX/1/1\">" + body + "</gpx>";
    }

    private static String point(String element, double lat, double lon) {
        return "<" + element + " lat=\"" + lat + "\" lon=\"" + lon + "\"><ele>12.5</ele><time>2026-03-05T09:00:00Z</time></" + element + ">";
    }

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** Trois points non alignés, à environ 1 km les uns des autres : tous doivent être conservés. */
    private static final String L_SHAPE = point("trkpt", 35.0, 139.0) + point("trkpt", 35.0, 139.011) + point("trkpt", 35.009, 139.011);

    @Test
    void litUnTraceAvecSesPointsDansLOrdre() {
        GpxParser.Result result = GpxParser.parse(stream(gpx("<trk><name>Train</name><trkseg>" + L_SHAPE + "</trkseg></trk>")));

        assertThat(result.originalCount()).isEqualTo(3);
        assertThat(result.points()).hasSize(3);
        assertThat(result.points().get(0)).containsExactly(35.0, 139.0);
        assertThat(result.points().get(2)).containsExactly(35.009, 139.011);
    }

    @Test
    void assembleToutesLesPistesEtSegmentsALaSuite() {
        String xml = gpx("<trk><trkseg>" + point("trkpt", 35.0, 139.0) + point("trkpt", 35.0, 139.011) + "</trkseg>"
                + "<trkseg>" + point("trkpt", 35.009, 139.011) + point("trkpt", 35.009, 139.0) + "</trkseg></trk>"
                + "<trk><trkseg>" + point("trkpt", 35.018, 139.0) + "</trkseg></trk>");

        GpxParser.Result result = GpxParser.parse(stream(xml));

        assertThat(result.originalCount()).isEqualTo(5);
        assertThat(result.points().get(0)).containsExactly(35.0, 139.0);
        assertThat(result.points().get(result.points().size() - 1)).containsExactly(35.018, 139.0);
    }

    @Test
    void utiliseUneRouteSiLeFichierNAPasDePiste() {
        GpxParser.Result result = GpxParser.parse(stream(gpx("<rte><name>Route</name>" + L_SHAPE.replace("trkpt", "rtept") + "</rte>")));

        assertThat(result.points()).hasSize(3);
    }

    @Test
    void lesWaypointsIsolesNeFormentPasUnTrace() {
        String xml = gpx(point("wpt", 35.0, 139.0) + point("wpt", 36.0, 140.0));

        assertThatThrownBy(() -> GpxParser.parse(stream(xml)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aucun tracé");
    }

    @Test
    void ignoreLesPointsInvalidesSansEchouer() {
        String xml = gpx("<trk><trkseg>" + L_SHAPE + point("trkpt", 95.0, 10.0) + point("trkpt", 10.0, 200.0)
                + "<trkpt lat=\"abc\" lon=\"10\"/><trkpt lat=\"10\"/></trkseg></trk>");

        assertThat(GpxParser.parse(stream(xml)).originalCount()).isEqualTo(3);
    }

    @Test
    void refuseCeQuiNEstPasUnGpx() {
        assertThatThrownBy(() -> GpxParser.parse(stream("ceci n'est pas du XML")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("illisible");
        assertThatThrownBy(() -> GpxParser.parse(stream("")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GpxParser.parse(stream("<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"1\"")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GpxParser.parse(stream(gpx("<trk><trkseg>" + point("trkpt", 35.0, 139.0) + "</trkseg></trk>"))))
                .isInstanceOf(IllegalArgumentException.class); // un seul point : pas un tracé
    }

    @Test
    void unFichierPiegeNeLitJamaisUnFichierLocalNiNeProvoqueUneAutreErreur() {
        String trap = "<?xml version=\"1.0\"?><!DOCTYPE gpx [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<gpx version=\"1.1\"><metadata><name>&xxe;</name></metadata><trk><trkseg>" + L_SHAPE + "</trkseg></trk></gpx>";
        try {
            GpxParser.parse(stream(trap)); // soit le tracé est lu sans résoudre l'entité...
        } catch (IllegalArgumentException expected) {
            // ... soit le fichier est refusé proprement : jamais une autre exception
        }
    }

    @Test
    void unTraceLongEstAlegeEtGardeSesExtremites() {
        Random random = new Random(3);
        StringBuilder points = new StringBuilder();
        double lat = 35.0;
        double lon = 139.0;
        double firstLat = lat;
        double lastLat = lat;
        int count = 50_000;
        for (int i = 0; i < count; i++) {
            lat += (random.nextDouble() - 0.4) * 0.0004;
            lon += (random.nextDouble() - 0.4) * 0.0004;
            lastLat = lat;
            points.append("<trkpt lat=\"").append(lat).append("\" lon=\"").append(lon).append("\"/>");
        }

        GpxParser.Result result = GpxParser.parse(stream(gpx("<trk><trkseg>" + points + "</trkseg></trk>")));

        assertThat(result.originalCount()).isEqualTo(count);
        assertThat(result.points().size()).isBetween(50, GpxParser.MAX_POINTS);
        assertThat(result.points().get(result.points().size() - 1)[0]).isEqualTo(lastLat);
    }

    @Test
    void supprimeLesPointsImmobiles() {
        StringBuilder points = new StringBuilder(point("trkpt", 35.0, 139.0));
        for (int i = 0; i < 200; i++) {
            points.append(point("trkpt", 35.0 + (i % 3) * 0.00001, 139.0 + (i % 2) * 0.00001)); // quelques mètres de bruit
        }
        points.append(point("trkpt", 35.01, 139.0));

        assertThat(GpxParser.parse(stream(gpx("<trk><trkseg>" + points + "</trkseg></trk>"))).points().size()).isLessThan(5);
    }

    @Test
    void laTraverseeDeLaLigneDeChangementDeDateResteContinue() {
        String xml = gpx("<trk><trkseg>" + point("trkpt", 10.0, 179.0) + point("trkpt", 10.5, 179.95)
                + point("trkpt", 11.0, -179.95) + point("trkpt", 11.5, -179.0) + "</trkseg></trk>");

        List<double[]> points = GpxParser.parse(stream(xml)).points();

        for (int i = 1; i < points.size(); i++) {
            assertThat(Math.abs(points.get(i)[1] - points.get(i - 1)[1])).isLessThan(10);
        }
        assertThat(points.get(points.size() - 1)[1]).isEqualTo(181.0);
    }
}

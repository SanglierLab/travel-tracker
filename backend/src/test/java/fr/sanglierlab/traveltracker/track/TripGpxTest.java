package fr.sanglierlab.traveltracker.track;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TripGpxTest {

    private static TrackPoint point(String lat, String lon, String at) {
        return new TrackPoint(TrackSource.ADSB, 7L, new BigDecimal(lat), new BigDecimal(lon), null, LocalDateTime.parse(at));
    }

    private static TrackedTrip trip(TripType type, String label, String identifier) {
        TrackedTrip trip = new TrackedTrip(type, label, LocalDateTime.parse("2026-10-12T00:00:00"), identifier);
        ReflectionTestUtils.setField(trip, "id", 7L);
        return trip;
    }

    @Test
    void ecritUnTraceUniqueAvecLHeureUtcALaMilliseconde() {
        String xml = TripGpx.build("Vol AFR1234", List.of(
                point("35.552100", "139.779600", "2026-10-12T09:30:15.250"),
                point("35.600000", "139.800000", "2026-10-12T09:31:15")));

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<name>Vol AFR1234</name>");
        assertThat(xml).contains("<trkpt lat=\"35.552100\" lon=\"139.779600\"><time>2026-10-12T09:30:15.250Z</time></trkpt>");
        assertThat(xml).contains("<time>2026-10-12T09:31:15.000Z</time>");
        assertThat(xml.split("<trkseg>", -1)).hasSize(2);
    }

    @Test
    void echappeLeNomEtLesCaracteresDeControle() {
        String xml = TripGpx.build("Tokyo <\"A&B\"> \u0001", List.of(point("1.000000", "2.000000", "2026-10-12T09:30:15")));

        assertThat(xml).contains("<name>Tokyo &lt;&quot;A&amp;B&quot;&gt; </name>");
    }

    @Test
    void nomDeFichierSansAccentNiCaractereSpecial() {
        assertThat(TripGpx.fileName(trip(TripType.PLANE, "Vol AFR1234", "AFR1234"))).isEqualTo("vol-AFR1234-2026-10-12.gpx");
        assertThat(TripGpx.fileName(trip(TripType.BOAT, "Croisière 227123456", "227123456"))).isEqualTo("croisiere-227123456-2026-10-12.gpx");
        assertThat(TripGpx.fileName(trip(TripType.LIVETRACK, "Tokyo – Asakusa / été", "uuid"))).isEqualTo("livetrack-Tokyo-Asakusa-ete-2026-10-12.gpx");
        assertThat(TripGpx.fileName(trip(TripType.LIVETRACK, "日本", "uuid"))).isEqualTo("livetrack-7-2026-10-12.gpx");
    }
}

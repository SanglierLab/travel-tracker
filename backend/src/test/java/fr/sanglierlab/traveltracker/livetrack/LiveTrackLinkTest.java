package fr.sanglierlab.traveltracker.livetrack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveTrackLinkTest {

    private static final String SESSION = "1debb03f-2acd-48b0-a8c9-2ba613bfcb3c";
    private static final String TOKEN = "5B72E4867D3AE6EB4B3D11F612044D8";

    @Test
    void analyseUnLienDePartage() {
        LiveTrackLink link = LiveTrackLink.parse("https://livetrack.garmin.com/session/" + SESSION + "/token/" + TOKEN).orElseThrow();

        assertThat(link.sessionId()).isEqualTo(SESSION);
        assertThat(link.token()).isEqualTo(TOKEN);
    }

    @Test
    void tolereEspacesSlashFinalEtParametres() {
        String base = "https://livetrack.garmin.com/session/" + SESSION + "/token/" + TOKEN;

        for (String raw : new String[]{"  " + base + "  ", base + "/", base + "?utm=x", base + "#carte", "\n" + base + "\n"}) {
            assertThat(LiveTrackLink.parse(raw)).as(raw).isPresent();
        }
    }

    @Test
    void l_identifiantEstMisEnMinuscules() {
        LiveTrackLink link = LiveTrackLink.parse("https://livetrack.garmin.com/session/" + SESSION.toUpperCase() + "/token/" + TOKEN).orElseThrow();

        assertThat(link.sessionId()).isEqualTo(SESSION);
    }

    @Test
    void refuseCeQuiNEstPasUnLienLiveTrack() {
        String[] invalid = {
                null,
                "",
                "   ",
                "n'importe quoi",
                "http://livetrack.garmin.com/session/" + SESSION + "/token/" + TOKEN,            // http
                "https://evil.example/session/" + SESSION + "/token/" + TOKEN,                    // autre site
                "https://livetrack.garmin.com.evil.example/session/" + SESSION + "/token/" + TOKEN,
                "https://evil.example/https://livetrack.garmin.com/session/" + SESSION + "/token/" + TOKEN,
                "https://livetrack.garmin.com/session/" + SESSION,                                // pas de jeton
                "https://livetrack.garmin.com/session/" + SESSION + "/token/",
                "https://livetrack.garmin.com/session/pas-un-uuid/token/" + TOKEN,
                "https://livetrack.garmin.com/session/" + SESSION + "/token/court",               // jeton trop court
                "https://livetrack.garmin.com/session/" + SESSION + "/token/" + TOKEN + "/../x",
        };
        for (String raw : invalid) {
            assertThat(LiveTrackLink.parse(raw)).as(String.valueOf(raw)).isEmpty();
        }
    }

    @Test
    void leJetonNeApparaitJamaisDansLaRepresentationTextuelle() {
        LiveTrackLink link = new LiveTrackLink(SESSION, TOKEN);

        assertThat(link.toString()).doesNotContain(TOKEN).contains("1debb03f");
    }

    @Test
    void reconnaitLesLiensCourtsGarmin() {
        assertThat(LiveTrackLink.isShortLink("https://gar.mn/poxXxXxXlA")).isTrue();
        assertThat(LiveTrackLink.isShortLink("  https://gar.mn/abc/ ")).isTrue();
        assertThat(LiveTrackLink.isShortLink("http://gar.mn/abcdef")).isFalse();
        assertThat(LiveTrackLink.isShortLink("https://gar.mn.evil.com/abc")).isFalse();
        assertThat(LiveTrackLink.isShortLink("https://gar.mn/a/b")).isFalse();
        assertThat(LiveTrackLink.isShortLink(null)).isFalse();
        // Un lien long n'est pas un lien court (et inversement) : le service teste d'abord le lien long.
        assertThat(LiveTrackLink.parse("https://gar.mn/poxXxXxXlA")).isEmpty();
    }
}

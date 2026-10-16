package fr.sanglierlab.traveltracker.livetrack.api;

import fr.sanglierlab.traveltracker.livetrack.LiveTrackException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarminShortLinkResolverTest {

    private static final String SESSION = "1debb03f-2acd-48b0-a8c9-2ba613bfcb3c";
    private static final String TOKEN = "5B72E4867D3AE6EB4B3D11F612044D8";
    private static final String LONG = "https://livetrack.garmin.com/session/" + SESSION + "/token/" + TOKEN;

    private final List<String> called = new ArrayList<>();

    private GarminShortLinkResolver resolver(Map<String, GarminShortLinkResolver.Hop> routes) {
        return new GarminShortLinkResolver(uri -> {
            called.add(uri.toString());
            GarminShortLinkResolver.Hop hop = routes.get(uri.toString());
            if (hop == null) {
                throw new IOException("inattendu " + uri);
            }
            return hop;
        });
    }

    @Test
    void suitLa301VersLeLienLong() {
        var resolver = resolver(Map.of("https://gar.mn/poxXxXxXlA", new GarminShortLinkResolver.Hop(301, LONG)));

        var link = resolver.resolve("https://gar.mn/poxXxXxXlA");

        assertThat(link).isPresent();
        assertThat(link.get().sessionId()).isEqualTo(SESSION);
        assertThat(link.get().token()).isEqualTo(TOKEN);
        assertThat(called).containsExactly("https://gar.mn/poxXxXxXlA");
    }

    @Test
    void accepteUneRedirectionIntermediaireSurGarMn() {
        var resolver = resolver(Map.of(
                "https://gar.mn/abc", new GarminShortLinkResolver.Hop(302, "/abc/"),
                "https://gar.mn/abc/", new GarminShortLinkResolver.Hop(301, LONG)));

        assertThat(resolver.resolve("https://gar.mn/abc")).isPresent();
        assertThat(called).hasSize(2);
    }

    @Test
    void refuseUneRedirectionVersUnAutreSiteSansLeContacter() {
        var resolver = resolver(Map.of("https://gar.mn/abc", new GarminShortLinkResolver.Hop(301, "https://evil.example/session/x")));

        assertThat(resolver.resolve("https://gar.mn/abc")).isEmpty();
        assertThat(called).containsExactly("https://gar.mn/abc");
    }

    @Test
    void refuseUneRedirectionVersHttp() {
        var resolver = resolver(Map.of("https://gar.mn/abc", new GarminShortLinkResolver.Hop(301, "http://gar.mn/abc2")));

        assertThat(resolver.resolve("https://gar.mn/abc")).isEmpty();
    }

    @Test
    void pasDeRedirectionOuLienInconnuDonneVide() {
        assertThat(resolver(Map.of("https://gar.mn/a1b", new GarminShortLinkResolver.Hop(404, null))).resolve("https://gar.mn/a1b")).isEmpty();
        assertThat(resolver(Map.of("https://gar.mn/a1b", new GarminShortLinkResolver.Hop(200, null))).resolve("https://gar.mn/a1b")).isEmpty();
        assertThat(resolver(Map.of("https://gar.mn/a1b", new GarminShortLinkResolver.Hop(301, null))).resolve("https://gar.mn/a1b")).isEmpty();
    }

    @Test
    void uneBoucleDeRedirectionsEstLimitee() {
        var resolver = resolver(Map.of(
                "https://gar.mn/a1b", new GarminShortLinkResolver.Hop(301, "https://gar.mn/a1c"),
                "https://gar.mn/a1c", new GarminShortLinkResolver.Hop(301, "https://gar.mn/a1b")));

        assertThat(resolver.resolve("https://gar.mn/a1b")).isEmpty();
        assertThat(called).hasSize(GarminShortLinkResolver.MAX_HOPS);
    }

    @Test
    void unReseauEnPanneDonneUneErreurSansAdresse() {
        var resolver = new GarminShortLinkResolver(uri -> {
            throw new IOException("boom " + uri);
        });

        assertThatThrownBy(() -> resolver.resolve("https://gar.mn/abc"))
                .isInstanceOf(LiveTrackException.class).hasMessageContaining("injoignable").hasMessageNotContaining("gar.mn");
    }
}

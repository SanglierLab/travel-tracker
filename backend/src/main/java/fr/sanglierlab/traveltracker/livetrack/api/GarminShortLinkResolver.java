package fr.sanglierlab.traveltracker.livetrack.api;

import fr.sanglierlab.traveltracker.livetrack.LiveTrackException;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackLink;
import fr.sanglierlab.traveltracker.livetrack.LiveTrackLinkResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Résout un lien court Garmin ({@code https://gar.mn/xxxx}, qui répond HTTP 301) vers le lien long LiveTrack.
 *
 * <p>Les redirections sont suivies <b>à la main</b>, pas par le client HTTP : chaque étape doit rester en HTTPS sur {@code gar.mn}, ou être
 * directement un lien LiveTrack valide (hôte {@code livetrack.garmin.com}). Un lien court qui redirige ailleurs est refusé : le serveur
 * ne contacte donc jamais une adresse choisie par l'utilisateur. Les adresses ne figurent dans aucun message d'erreur.
 */
@Service
public class GarminShortLinkResolver implements LiveTrackLinkResolver {

    static final int MAX_HOPS = 3;
    private static final String SHORT_HOST = "gar.mn";

    /** Réponse d'une étape : statut HTTP et en-tête Location. */
    record Hop(int status, String location) {
    }

    /** Un appel HTTP sans suivi de redirection (remplaçable dans les tests). */
    @FunctionalInterface
    interface Fetcher {
        Hop get(URI uri) throws IOException, InterruptedException;
    }

    private final Fetcher fetcher;

    @Autowired
    public GarminShortLinkResolver() {
        this(httpFetcher());
    }

    GarminShortLinkResolver(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    private static Fetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "travel-tracker/2.0 (+https://voyage.sanglierlab.fr)").build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return new Hop(response.statusCode(), response.headers().firstValue("Location").orElse(null));
        };
    }

    @Override
    public Optional<LiveTrackLink> resolve(String shortLink) {
        URI current = URI.create(shortLink.strip());
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            Hop response;
            try {
                response = fetcher.get(current);
            } catch (IOException e) {
                throw new LiveTrackException("Garmin injoignable (" + e.getClass().getSimpleName() + ")");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LiveTrackException("Résolution du lien interrompue");
            }
            if (response.status() < 300 || response.status() > 399 || response.location() == null) {
                return Optional.empty();
            }
            URI next;
            try {
                next = current.resolve(response.location().strip());
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            Optional<LiveTrackLink> link = LiveTrackLink.parse(next.toString());
            if (link.isPresent()) {
                return link;
            }
            if (!"https".equalsIgnoreCase(next.getScheme()) || !SHORT_HOST.equalsIgnoreCase(next.getHost())) {
                return Optional.empty();
            }
            current = next;
        }
        return Optional.empty();
    }
}

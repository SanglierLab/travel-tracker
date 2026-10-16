package fr.sanglierlab.traveltracker.track.garmin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXPLORATION MANUELLE de Garmin LiveTrack : à lancer à la main dans IntelliJ (flèche verte à côté d'une méthode), jamais par Maven.
 * Le nom ne se termine pas par « Test » exprès : Surefire (mvn test) l'ignore, car il a besoin d'une vraie session et du réseau.
 *
 * <p>Mode d'emploi :
 * <ol>
 *   <li>Démarrer un LiveTrack sur la montre, puis coller ici le lien « à partager » dans {@link #LIVETRACK_LINK}.</li>
 *   <li>Lancer d'abord {@link #pageDuLien()} puis {@link #graphqlTypename()} : ils disent si Garmin répond à un client Java
 *       (200 attendu ; 403 ou page de vérification = Garmin bloque les clients hors navigateur).</li>
 *   <li>Dans le navigateur (outils de développement, onglet Réseau, ouvrir le lien), repérer la requête qui charge les positions,
 *       la copier en cURL, puis reporter méthode, URL, en-têtes et corps dans {@link #rejouerDepuisLeNavigateur()}.</li>
 * </ol>
 *
 * <p>Le jeton du lien donne accès à la position en direct : il est masqué dans les sorties de ce test, mais ne pas le commiter ni le coller en public.
 */
class GarminLiveTrackManual {

    /** À REMPLACER : le lien reçu par e-mail ou depuis la montre, de la forme https://livetrack.garmin.com/session/{uuid}/token/{jeton}. */
    static final String LIVETRACK_LINK = "https://livetrack.garmin.com/session/00000000-0000-0000-0000-000000000000/token/00000000000000000000000000000000";

    /** Mettre à true pour se présenter comme un navigateur (utile pour voir si c'est le User-Agent qui est filtré). */
    static final boolean FAKE_BROWSER = false;

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String OUR_USER_AGENT = "travel-tracker/2.0 (+https://voyage.sanglierlab.fr)";

    private static final Pattern LINK = Pattern.compile("^(https?://[^/]+)/session/([0-9a-fA-F-]{36})/token/([0-9a-zA-Z]+)");

    private static final Matcher PARSED = LINK.matcher(LIVETRACK_LINK);

    static {
        if (!PARSED.find()) {
            throw new IllegalStateException("LIVETRACK_LINK n'a pas la forme https://livetrack.garmin.com/session/{uuid}/token/{jeton}");
        }
    }

    private static final String BASE_URL = PARSED.group(1);
    private static final String SESSION_ID = PARSED.group(2);
    private static final String TOKEN = PARSED.group(3);

    /** Même construction que AdsbLolClient : client JDK, délais de connexion et de lecture. */
    private final RestClient restClient = buildClient();

    private static RestClient buildClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", FAKE_BROWSER ? BROWSER_USER_AGENT : OUR_USER_AGENT)
                .build();
    }

    // -----------------------------------------------------------------------
    // 1. La page du lien : Garmin répond-il à un client Java ? (HTML attendu ; 403 / « Just a moment » = blocage)

    @Test
    void pageDuLien() {
        send("Page du lien", HttpMethod.GET, LIVETRACK_LINK, Map.of("Accept", "text/html"), null);
    }

    // -----------------------------------------------------------------------
    // 1b. Où est la position dans la page ? Sauvegarde le HTML et montre les passages qui ressemblent à des données.

    /**
     * Enregistre la page dans {@code target/garmin-livetrack-page.html} (à ouvrir dans un éditeur : le jeton y apparaît, ne pas la partager)
     * et affiche : les balises {@code <script>} qui contiennent du JSON (état initial type Next.js / Apollo) et les extraits autour des mots
     * qui annoncent une position. Coller ces extraits (jeton masqué) permet d'écrire l'extraction exacte.
     */
    @Test
    void analysePage() throws Exception {
        String html = fetchText(LIVETRACK_LINK);
        Path file = Path.of("target", "garmin-livetrack-page.html");
        Files.createDirectories(file.getParent());
        Files.writeString(file, html);
        System.out.println("Page enregistrée : " + file.toAbsolutePath() + " (" + html.length() + " caractères)");

        Matcher scripts = Pattern.compile("<script([^>]*)>(.*?)</script>", Pattern.DOTALL).matcher(html);
        int index = 0;
        while (scripts.find()) {
            String attributes = scripts.group(1).trim();
            String content = scripts.group(2);
            boolean json = attributes.contains("application/json") || attributes.contains("ld+json")
                    || content.contains("__NEXT_DATA__") || content.contains("__APOLLO_STATE__") || content.contains("window.__");
            System.out.println("script #" + index++ + " [" + (attributes.isEmpty() ? "-" : mask(attributes)) + "] " + content.length() + " car." + (json ? "  <-- données possibles" : ""));
        }

        for (String keyword : new String[]{"__NEXT_DATA__", "__APOLLO_STATE__", "trackPoints", "\"position\"", "latitude", "\"lat\"", "dateTime"}) {
            int from = 0;
            int shown = 0;
            while (shown < 2 && (from = html.indexOf(keyword, from)) >= 0) {
                int start = Math.max(0, from - 120);
                int end = Math.min(html.length(), from + 400);
                System.out.println("\n[" + keyword + " @" + from + "] ..." + mask(html.substring(start, end).replace("\n", " ")) + "...");
                from += keyword.length();
                shown++;
            }
        }
    }

    /**
     * Charge la page deux fois à 30 s d'écart et dit si le contenu a changé : si oui, la position est bien dans le HTML renvoyé par le
     * serveur à chaque appel (un simple GET par cycle suffira) ; sinon elle est chargée ensuite par le navigateur (autre requête à trouver).
     */
    @Test
    void laPageChangeTelleEntreDeuxAppels() throws Exception {
        String first = fetchText(LIVETRACK_LINK);
        Thread.sleep(30_000);
        String second = fetchText(LIVETRACK_LINK);
        if (first.equals(second)) {
            System.out.println("IDENTIQUES : la page ne contient pas de position qui évolue (ou aucune nouvelle position en 30 s).");
            return;
        }
        int i = 0;
        while (i < Math.min(first.length(), second.length()) && first.charAt(i) == second.charAt(i)) {
            i++;
        }
        System.out.println("DIFFÉRENTES (premier écart à " + i + ")");
        System.out.println("appel 1 : ..." + mask(first.substring(Math.max(0, i - 80), Math.min(first.length(), i + 200)).replace("\n", " ")));
        System.out.println("appel 2 : ..." + mask(second.substring(Math.max(0, i - 80), Math.min(second.length(), i + 200)).replace("\n", " ")));
    }

    // -----------------------------------------------------------------------
    // 1c. Combien pèse la page, et peut-on l'alléger ? (compression, requête conditionnelle)

    /**
     * Mesure le coût d'un appel : taille sans compression, avec {@code Accept-Encoding: gzip}, et réponse à une requête conditionnelle
     * ({@code If-None-Match} / {@code If-Modified-Since}) si le serveur donne un ETag ou une date. Un 304 voudrait dire « rien de nouveau,
     * rien à télécharger » ; un gros gain de gzip rend un appel par minute acceptable même après des heures de session.
     */
    @Test
    void mesureLaPage() throws Exception {
        measure("sans compression", Map.of("Accept", "text/html"));
        String[] validators = measure("avec gzip", Map.of("Accept", "text/html", "Accept-Encoding", "gzip"));
        if (validators[0] != null || validators[1] != null) {
            Map<String, String> conditional = new LinkedHashMap<>();
            conditional.put("Accept", "text/html");
            if (validators[0] != null) {
                conditional.put("If-None-Match", validators[0]);
            }
            if (validators[1] != null) {
                conditional.put("If-Modified-Since", validators[1]);
            }
            measure("requête conditionnelle", conditional);
        } else {
            System.out.println("Pas d'ETag ni de Last-Modified : pas de requête conditionnelle possible.");
        }
    }

    /** @return {ETag, Last-Modified} de la réponse (null si absents) */
    private String[] measure(String title, Map<String, String> headers) {
        return restClient.get().uri(URI.create(LIVETRACK_LINK)).headers(h -> headers.forEach(h::set)).exchange((req, response) -> {
            byte[] raw = response.getBody().readAllBytes();
            String encoding = response.getHeaders().getFirst("Content-Encoding");
            int decoded = raw.length;
            if ("gzip".equalsIgnoreCase(encoding)) {
                decoded = new GZIPInputStream(new ByteArrayInputStream(raw)).readAllBytes().length;
            }
            System.out.println("[" + title + "] HTTP " + response.getStatusCode() + " | corps reçu " + raw.length + " octets"
                    + " | Content-Encoding=" + encoding + " | une fois décompressé " + decoded + " octets");
            for (String name : new String[]{"Cache-Control", "ETag", "Last-Modified", "Vary", "Age", "Content-Length"}) {
                String value = response.getHeaders().getFirst(name);
                if (value != null) {
                    System.out.println("   " + name + ": " + value);
                }
            }
            return new String[]{response.getHeaders().getFirst("ETag"), response.getHeaders().getFirst("Last-Modified")};
        });
    }

    // -----------------------------------------------------------------------
    // 1d. Extraction des points directement dans le HTML (sans dépendre de la structure de la page)

    /**
     * Les points sont dans un flux de données de la page (échappé dans des chaînes JavaScript : {@code \"dateTime\":\"...\"}). On retire
     * les échappements puis on cherche {@code dateTime / reportedTime / position{lat,lon}} : pas besoin de comprendre toute la page.
     * Affiche le nombre de points, la plage de temps, l'intervalle entre points et la dernière position (arrondie).
     */
    @Test
    void extrairePoints() {
        String html = fetchText(LIVETRACK_LINK);
        String text = html.replaceAll("\\\\+\"", "\"");
        Matcher matcher = Pattern.compile(
                        "\"dateTime\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"reportedTime\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"position\"\\s*:\\s*\\{\\s*\"lat\"\\s*:\\s*(-?[0-9.]+)\\s*,\\s*\"lon\"\\s*:\\s*(-?[0-9.]+)")
                .matcher(text);
        TreeMap<String, String[]> byTime = new TreeMap<>(); // trié par heure, sans doublons
        int found = 0;
        while (matcher.find()) {
            found++;
            byTime.put(matcher.group(1), new String[]{matcher.group(2), matcher.group(3), matcher.group(4)});
        }
        System.out.println("Occurrences trouvées : " + found + " | points distincts : " + byTime.size());
        if (byTime.isEmpty()) {
            System.out.println("Aucun point reconnu : coller dans le chat un extrait BRUT (non reformaté) du fichier autour de \"dateTime\".");
            return;
        }
        String firstTime = byTime.firstKey();
        String lastTime = byTime.lastKey();
        String[] last = byTime.lastEntry().getValue();
        System.out.println("Premier point : " + firstTime + " | dernier : " + lastTime + " (reçu " + last[0] + ")");
        long seconds = java.time.Duration.between(java.time.Instant.parse(firstTime), java.time.Instant.parse(lastTime)).toSeconds();
        System.out.println("Durée couverte : " + seconds + " s, soit un point toutes les " + (byTime.size() > 1 ? seconds / (byTime.size() - 1) : 0) + " s en moyenne");
        System.out.printf("Dernière position : %.3f, %.3f%n", Double.parseDouble(last[1]), Double.parseDouble(last[2]));
    }

    // -----------------------------------------------------------------------
    // 1e. L'API que la page appelle elle-même pour les nouveaux points : /api/sessions/{id}/track-points/common?token=...&begin=...
    //     Collée dans la barre d'adresse du navigateur elle répond « Forbidden » : elle attend sans doute les en-têtes ou les cookies de la page.

    /**
     * Essaie la même requête avec des en-têtes de plus en plus proches de ceux du navigateur, d'abord sans cookie puis après avoir chargé la page
     * (qui peut poser des cookies), et affiche un statut par variante. La première variante qui répond 200 donne la recette minimale :
     * on la reproduira telle quelle dans le client. Si TOUT reste en 403, copier la requête du navigateur en cURL (clic droit > Copier) et
     * la reporter dans {@link #rejouerDepuisLeNavigateur()}, puis retirer ses en-têtes un par un pour trouver celui qui compte.
     */
    @Test
    void apiPointsVariantes() {
        String begin = URLEncoder.encode(Instant.now().minus(Duration.ofMinutes(10)).toString(), StandardCharsets.UTF_8);
        String api = BASE_URL + "/api/sessions/" + SESSION_ID + "/track-points/common?token=" + TOKEN + "&begin=" + begin;

        Map<String, Map<String, String>> variants = new LinkedHashMap<>();
        variants.put("A nue", Map.of());
        variants.put("B + Accept JSON", Map.of("Accept", "application/json"));
        variants.put("C + Referer de la page", Map.of("Accept", "application/json", "Referer", LIVETRACK_LINK));
        Map<String, String> browserLike = new LinkedHashMap<>();
        browserLike.put("Accept", "*/*");
        browserLike.put("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8");
        browserLike.put("Referer", LIVETRACK_LINK);
        browserLike.put("Origin", BASE_URL);
        browserLike.put("Sec-Fetch-Dest", "empty");
        browserLike.put("Sec-Fetch-Mode", "cors");
        browserLike.put("Sec-Fetch-Site", "same-origin");
        variants.put("D comme un navigateur (Referer, Origin, Sec-Fetch-*)", browserLike);

        CookieManager cookies = new CookieManager();
        RestClient client = clientWith(cookies, BROWSER_USER_AGENT);

        System.out.println("##### Sans cookie");
        variants.forEach((name, headers) -> System.out.println(name + " -> " + status(client, api, headers)));

        System.out.println("\n##### Après avoir chargé la page du lien (cookies éventuels conservés)");
        System.out.println("Page -> " + status(client, LIVETRACK_LINK, Map.of("Accept", "text/html")).split("\n")[0]);
        System.out.println("Cookies reçus : " + cookies.getCookieStore().getCookies().stream().map(c -> c.getName()).toList());
        variants.forEach((name, headers) -> System.out.println(name + " -> " + status(client, api, headers)));
    }

    private static RestClient clientWith(CookieManager cookies, String userAgent) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).cookieHandler(cookies).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", userAgent).build();
    }

    /** « HTTP 403 | début du corps » : une ligne par essai. */
    private static String status(RestClient client, String url, Map<String, String> headers) {
        try {
            return client.get().uri(URI.create(url)).headers(h -> headers.forEach(h::set)).exchange((req, response) -> {
                String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                String shown = text.length() > 160 ? text.substring(0, 160) + "..." : text;
                return "HTTP " + response.getStatusCode().value() + " | " + mask(shown.replace("\n", " "));
            });
        } catch (Exception e) {
            return "ÉCHEC : " + e;
        }
    }

    // -----------------------------------------------------------------------
    // 1f. Rejouer la requête RÉELLE du navigateur, puis retirer ses en-têtes un par un pour trouver ceux qui comptent.

    /**
     * Fichier où coller la requête du navigateur : outils de développement > Réseau > clic droit sur la requête « track-points » >
     * Copier > « Copier en tant que cURL (bash) », puis tout coller dans ce fichier (à la racine du module, à côté de pom.xml).
     * Il contient le jeton et les cookies : ne pas le commiter (l'ajouter à .gitignore). Un fichier évite d'avoir à échapper les « \\ » dans le code.
     */
    static final Path CURL_FILE = Path.of("garmin-curl.txt");

    /**
     * Lit l'URL, les en-têtes (-H) et les cookies (-b) du cURL collé, rejoue la requête telle quelle (HTTP/2 puis HTTP/1.1), puis la rejoue
     * en retirant un en-tête à la fois : un en-tête dont le retrait fait passer de 200 à 403 est REQUIS. Si même la requête complète
     * échoue, la cause n'est pas un en-tête : cookie expiré (relancer le cURL depuis un navigateur tout neuf), ou filtrage par empreinte TLS
     * (dans ce cas, essayer aussi le même cURL dans un terminal : s'il passe en curl mais pas ici, c'est l'empreinte du client Java).
     */
    @Test
    void bisecterLeCurl() throws Exception {
        if (!Files.exists(CURL_FILE)) {
            System.out.println("Fichier introuvable : " + CURL_FILE.toAbsolutePath() + " (y coller la requête copiée en « cURL (bash) »).");
            return;
        }
        String curl = Files.readString(CURL_FILE);
        Matcher urlMatcher = Pattern.compile("'(https?://[^']+)'").matcher(curl);
        if (!urlMatcher.find()) {
            System.out.println("Aucune URL entre quotes simples dans " + CURL_FILE + " : copier en « cURL (bash) ».");
            return;
        }
        String url = urlMatcher.group(1);
        Map<String, String> headers = new LinkedHashMap<>();
        Matcher headerMatcher = Pattern.compile("(?:-H|--header)\\s+'([^':]+):\\s*([^']*)'").matcher(curl);
        while (headerMatcher.find()) {
            headers.put(headerMatcher.group(1).trim(), headerMatcher.group(2));
        }
        Matcher cookieMatcher = Pattern.compile("(?:-b|--cookie)\\s+'([^']*)'").matcher(curl);
        if (cookieMatcher.find()) {
            headers.put("Cookie", cookieMatcher.group(1));
        }
        // Java ne décompresse pas : on ne demande donc pas de compression (on ne regarde que le statut de toute façon).
        headers.keySet().removeIf(name -> name.equalsIgnoreCase("accept-encoding"));

        System.out.println("URL : " + mask(url));
        System.out.println("Le jeton de cette URL est identique à celui du lien : " + url.contains("token=" + TOKEN)
                + "  (non = l'API utilise un autre jeton, à repérer dans la page)");
        System.out.println("En-têtes lus : " + headers.keySet());

        for (HttpClient.Version version : new HttpClient.Version[]{HttpClient.Version.HTTP_2, HttpClient.Version.HTTP_1_1}) {
            RestClient client = clientWithVersion(version);
            String full = status(client, url, headers);
            System.out.println("\n##### " + version + " : requête complète -> " + full);
            if (!full.startsWith("HTTP 200")) {
                continue;
            }
            for (String name : headers.keySet()) {
                Map<String, String> without = new LinkedHashMap<>(headers);
                without.remove(name);
                String result = status(client, url, without);
                String code = result.split("\\|")[0].trim();
                System.out.println("  sans " + name + " -> " + code + (code.startsWith("HTTP 200") ? "" : "   <-- REQUIS"));
            }
        }
    }

    private static RestClient clientWithVersion(HttpClient.Version version) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(version).connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }

    // -----------------------------------------------------------------------
    // 1g. Le cookie et l'en-tête « livetrack-csrf-token » sont requis : d'où viennent-ils ? (protection CSRF : cookie posé par la page + jeton à renvoyer)

    /**
     * Compare, dans le cURL du fichier, la valeur de l'en-tête {@code livetrack-csrf-token} avec celle de chaque cookie. Si elle est
     * identique à celle d'un cookie, c'est un « double submit » : le jeton est simplement la valeur d'un cookie posé par la page.
     * Seules des longueurs et des noms sont affichés, jamais les valeurs.
     */
    @Test
    void analyseCsrfDuCurl() throws Exception {
        String curl = Files.readString(CURL_FILE);
        Matcher header = Pattern.compile("(?i)(?:-H|--header)\\s+'livetrack-csrf-token:\\s*([^']*)'").matcher(curl);
        Matcher cookie = Pattern.compile("(?:-b|--cookie)\\s+'([^']*)'").matcher(curl);
        if (!header.find() || !cookie.find()) {
            System.out.println("En-tête livetrack-csrf-token ou cookies introuvables dans " + CURL_FILE);
            return;
        }
        String csrf = header.group(1).trim();
        System.out.println("livetrack-csrf-token : " + csrf.length() + " caractères");
        for (String pair : cookie.group(1).split(";\\s*")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            String relation = value.equals(csrf) ? "  <-- IDENTIQUE au jeton csrf (double submit)" : (!csrf.isEmpty() && value.contains(csrf) ? "  <-- CONTIENT le jeton csrf" : "");
            System.out.println("cookie " + name + " : " + value.length() + " caractères" + relation);
        }
    }

    /**
     * Reproduit ce que fait la page : charge le lien (le serveur peut poser des cookies), cherche un jeton CSRF (valeur d'un cookie ou texte
     * dans le HTML), puis appelle l'API des points avec cookie + en-tête {@code livetrack-csrf-token}, pour chaque jeton candidat. Un 200
     * donne la recette complète pour le client. Affiche aussi les passages de la page qui parlent de « csrf » (valeurs raccourcies).
     */
    @Test
    void fluxComplet() {
        CookieManager cookies = new CookieManager();
        RestClient client = clientWith(cookies, BROWSER_USER_AGENT);
        String html = client.get().uri(URI.create(LIVETRACK_LINK)).header("Accept", "text/html").exchange((req, response) -> {
            System.out.println("Page du lien -> HTTP " + response.getStatusCode());
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        });
        String text = html.replaceAll("\\\\+\"", "\"");

        System.out.println("Cookies posés par la page :");
        for (HttpCookie c : cookies.getCookieStore().getCookies()) {
            System.out.println("   " + c.getName() + " (" + c.getValue().length() + " car., domaine " + c.getDomain() + ", chemin " + c.getPath() + ")");
        }

        Map<String, String> candidates = new LinkedHashMap<>();
        for (HttpCookie c : cookies.getCookieStore().getCookies()) {
            candidates.put("valeur du cookie " + c.getName(), c.getValue());
        }
        Matcher meta = Pattern.compile("(?i)<meta[^>]+csrf[^>]+content=\"([^\"]+)\"").matcher(text);
        while (meta.find()) {
            candidates.put("balise meta csrf #" + candidates.size(), meta.group(1));
        }
        Matcher inText = Pattern.compile("(?i)csrf[-_a-z]*[\"']?\\s*[:=]\\s*[\\\\\"']*([A-Za-z0-9._~+/=-]{16,})").matcher(text);
        while (inText.find() && candidates.size() < 12) {
            candidates.put("texte de la page près de « csrf » #" + candidates.size(), inText.group(1));
        }

        Matcher context = Pattern.compile("(?i)csrf").matcher(text);
        for (int shown = 0; shown < 5 && context.find(); shown++) {
            int from = Math.max(0, context.start() - 100);
            int to = Math.min(text.length(), context.end() + 160);
            System.out.println("[csrf @" + context.start() + "] ..." + redact(text.substring(from, to).replace("\n", " ")) + "...");
        }

        String begin = URLEncoder.encode(Instant.now().minus(Duration.ofMinutes(10)).toString(), StandardCharsets.UTF_8);
        String api = BASE_URL + "/api/sessions/" + SESSION_ID + "/track-points/common?token=" + TOKEN + "&begin=" + begin;
        System.out.println("\nAppels de l'API avec les cookies de la page :");
        System.out.println("   sans en-tête csrf -> " + status(client, api, Map.of("Accept", "application/json")));
        for (Map.Entry<String, String> candidate : candidates.entrySet()) {
            String result = status(client, api, Map.of("Accept", "application/json", "livetrack-csrf-token", candidate.getValue()));
            System.out.println("   " + candidate.getKey() + " (" + candidate.getValue().length() + " car.) -> " + result);
        }
    }

    /** Raccourcit toute longue suite de caractères de jeton : on voit où ils sont sans les exposer. */
    private static String redact(String text) {
        return mask(text).replaceAll("[A-Za-z0-9._~+/=-]{16,}", "…");
    }

    // -----------------------------------------------------------------------
    // 1h. Le flux fonctionne (cookies de la page + jeton de la balise <meta>) : que renvoie l'API, et comment dit-elle « rien de nouveau » ?

    /**
     * Charge la page (cookies + jeton CSRF de la balise {@code <meta>}), puis appelle l'API des points avec trois valeurs de {@code begin} :
     * il y a 2 heures (doit renvoyer des points), dans une minute (le cas « rien de nouveau »), et sans {@code begin}. Affiche pour chaque appel
     * le statut, le type de contenu, la taille et le début du corps : c'est ce qui fixe le format à analyser dans le vrai client.
     * Le corps contient des positions réelles : ne coller dans le chat que ce qui est utile, et masquer les coordonnées si besoin.
     */
    @Test
    void apiPointsContenu() {
        CookieManager cookies = new CookieManager();
        RestClient client = clientWith(cookies, BROWSER_USER_AGENT);
        String html = client.get().uri(URI.create(LIVETRACK_LINK)).header("Accept", "text/html").exchange((req, response) ->
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));

        Matcher meta = Pattern.compile("(?i)<meta[^>]+name=\"([^\"]*csrf[^\"]*)\"[^>]+content=\"([^\"]+)\"").matcher(html);
        Matcher metaReversed = Pattern.compile("(?i)<meta[^>]+content=\"([^\"]+)\"[^>]+name=\"([^\"]*csrf[^\"]*)\"").matcher(html);
        String csrfName;
        String csrf;
        if (meta.find()) {
            csrfName = meta.group(1);
            csrf = meta.group(2);
        } else if (metaReversed.find()) {
            csrfName = metaReversed.group(2);
            csrf = metaReversed.group(1);
        } else {
            System.out.println("Aucune balise <meta ...csrf...> trouvée dans la page.");
            return;
        }
        System.out.println("Balise <meta name=\"" + csrfName + "\"> : jeton de " + csrf.length() + " caractères");
        System.out.println("Cookies : " + cookies.getCookieStore().getCookies().stream().map(HttpCookie::getName).toList());

        String base = BASE_URL + "/api/sessions/" + SESSION_ID + "/track-points/common?token=" + TOKEN;
        Map<String, String> calls = new LinkedHashMap<>();
        calls.put("begin = il y a 2 h", base + "&begin=" + encodedInstant(Instant.now().minus(Duration.ofHours(2))));
        calls.put("begin = dans 1 min (rien de nouveau)", base + "&begin=" + encodedInstant(Instant.now().plus(Duration.ofMinutes(1))));
        calls.put("sans begin", base);
        for (Map.Entry<String, String> call : calls.entrySet()) {
            System.out.println("\n=== " + call.getKey() + " ===");
            try {
                client.get().uri(URI.create(call.getValue())).header("Accept", "application/json").header("livetrack-csrf-token", csrf).exchange((req, response) -> {
                    byte[] raw = response.getBody().readAllBytes();
                    String text = new String(raw, StandardCharsets.UTF_8);
                    System.out.println("HTTP " + response.getStatusCode() + " | Content-Type: " + response.getHeaders().getFirst("Content-Type")
                            + " | Content-Encoding: " + response.getHeaders().getFirst("Content-Encoding") + " | " + raw.length + " octets");
                    int occurrences = text.split("\"dateTime\"", -1).length - 1;
                    System.out.println("Occurrences de \"dateTime\" : " + occurrences);
                    System.out.println(mask(text.length() > 600 ? text.substring(0, 600) + " [...]" : text));
                    return null;
                });
            } catch (Exception e) {
                System.out.println("ÉCHEC : " + e);
            }
        }
    }

    private static String encodedInstant(Instant instant) {
        return URLEncoder.encode(instant.toString(), StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // 2. L'API GraphQL (utilisée par le site en 2023) : la requête la plus simple possible. 200 + {"data":{"__typename":"Query"}} = joignable.

    @Test
    void graphqlTypename() {
        send("GraphQL __typename", HttpMethod.POST, BASE_URL + "/apollo/graphql", jsonHeaders(), Map.of("query", "{ __typename }"));
    }

    /**
     * Les deux requêtes connues par les projets existants (sessionById, trackPointsBySessionId). La FORME EXACTE de la requête est une
     * déduction à partir des noms de champs publiés : si Garmin répond par une erreur de validation GraphQL, son message dit quoi corriger
     * (le plus fiable reste de recopier la requête du navigateur dans {@link #rejouerDepuisLeNavigateur()}).
     */
    @Test
    void graphqlSessionEtPositions() {
        String query = """
                query($sessionId: String!, $token: String!) {
                  sessionById(sessionId: $sessionId, token: $token) { sessionId start end }
                  trackPointsBySessionId(sessionId: $sessionId, token: $token) { dateTime speed altitude position { lat lon } }
                }""";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("variables", Map.of("sessionId", SESSION_ID, "token", TOKEN));
        send("GraphQL session + positions", HttpMethod.POST, BASE_URL + "/apollo/graphql", jsonHeaders(), body);
    }

    // -----------------------------------------------------------------------
    // 3. L'ancienne API REST (projets de 2016 à 2020) : sans doute disparue, mais le test ne coûte rien.

    @Test
    void restSessionHistorique() {
        send("REST session", HttpMethod.GET,
                BASE_URL + "/services/session/" + SESSION_ID + "/token/" + TOKEN + "?requestTime=" + System.currentTimeMillis(),
                Map.of("Accept", "application/json"), null);
    }

    @Test
    void restTrackLogHistorique() {
        long since = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis();
        send("REST trackLog", HttpMethod.GET,
                BASE_URL + "/services/trackLog/" + SESSION_ID + "/token/" + TOKEN + "?requestTime=" + System.currentTimeMillis() + "&from=" + since,
                Map.of("Accept", "application/json"), null);
    }

    // -----------------------------------------------------------------------
    // 4. Rejouer EXACTEMENT la requête vue dans le navigateur (outils de développement > Réseau > clic droit > Copier > cURL).

    @Test
    void rejouerDepuisLeNavigateur() {
        HttpMethod method = HttpMethod.POST;
        String url = BASE_URL + "/apollo/graphql";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "*/*");
        // headers.put("Origin", BASE_URL);   // à décommenter / compléter d'après le cURL du navigateur
        // headers.put("Referer", LIVETRACK_LINK);
        // headers.put("Cookie", "...");
        String body = "{\"query\":\"{ __typename }\"}"; // à remplacer par le corps (--data-raw) du cURL
        send("Requête du navigateur", method, url, headers, body);
    }

    // -----------------------------------------------------------------------

    private static Map<String, String> jsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.put("Accept", MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    /** GET simple qui renvoie le corps (et affiche le statut) ; les erreurs HTTP ne lèvent pas d'exception. */
    private String fetchText(String url) {
        return restClient.get().uri(URI.create(url)).header("Accept", "text/html").exchange((req, response) -> {
            System.out.println("GET " + mask(url) + " -> HTTP " + response.getStatusCode());
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        });
    }

    /** Envoie la requête et AFFICHE le résultat sans jamais lever d'exception sur un 4xx / 5xx (c'est justement ce qu'on veut voir). */
    private void send(String title, HttpMethod method, String url, Map<String, String> headers, Object body) {
        System.out.println("=== " + title + " ===");
        System.out.println(method + " " + mask(url));
        try {
            RestClient.RequestBodySpec request = restClient.method(method).uri(URI.create(url)).headers(h -> headers.forEach(h::set));
            if (body != null) {
                request = request.body(body);
            }
            request.exchange((req, response) -> {
                System.out.println("HTTP " + response.getStatusCode());
                // En-têtes qui trahissent un pare-feu ou un service de protection (Cloudflare, Akamai...)
                for (String name : new String[]{"Content-Type", "Server", "Via", "Set-Cookie", "CF-Ray", "X-Akamai-Request-ID", "Retry-After", "Location"}) {
                    String value = response.getHeaders().getFirst(name);
                    if (value != null) {
                        System.out.println(name + ": " + mask(value));
                    }
                }
                String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("--- corps (" + text.length() + " caractères, tronqué à 3000) ---");
                System.out.println(mask(text.length() > 3000 ? text.substring(0, 3000) + " [...]" : text));
                return null;
            });
        } catch (Exception e) {
            System.out.println("ÉCHEC avant réponse : " + e);
        }
        System.out.println();
    }

    /** Le jeton ne s'affiche jamais en entier. */
    private static String mask(String text) {
        return text.replace(TOKEN, TOKEN.substring(0, Math.min(4, TOKEN.length())) + "…");
    }
}

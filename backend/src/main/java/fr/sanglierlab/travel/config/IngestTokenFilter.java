package fr.sanglierlab.travel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authentifie l'API d'ingestion par jeton porteur.
 *
 *   Authorization: Bearer &lt;app.ingest-token&gt;
 *
 * Volontairement rudimentaire : un seul jeton partagé, défini dans la
 * configuration serveur. Le tracker mobile n'a rien d'autre à gérer.
 *
 * Le filtre ne rejette pas lui-même : il se contente de renseigner (ou non)
 * le contexte de sécurité. C'est la chaîne de filtres qui refuse l'accès,
 * ce qui garantit une réponse 401 homogène avec le reste de l'application.
 */
public class IngestTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IngestTokenFilter.class);
    private static final String PREFIX = "Bearer ";

    private final byte[] expectedToken;

    public IngestTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(PREFIX)) {
            byte[] provided = header.substring(PREFIX.length()).trim()
                    .getBytes(StandardCharsets.UTF_8);

            // Comparaison à temps constant : pas d'attaque par mesure de temps
            if (MessageDigest.isEqual(expectedToken, provided)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "tracker", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + Roles.INGEST)));

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } else {
                log.warn("Jeton d'ingestion invalide reçu depuis {}", request.getRemoteAddr());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();   // chaîne sans état
        }
    }
}

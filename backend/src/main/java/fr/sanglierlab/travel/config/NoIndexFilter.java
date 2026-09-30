package fr.sanglierlab.travel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Interdiction d'indexation, appliquée à toutes les réponses du backend.
 *
 * Troisième et dernier rempart, après :
 *  - la balise &lt;meta name="robots"&gt; de index.html
 *  - le fichier robots.txt (frontend et backend)
 *
 * L'en-tête HTTP est le seul des trois qui protège aussi les médias servis
 * directement : une photo ouverte en plein écran ne doit pas finir dans
 * Google Images.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoIndexFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Robots-Tag";
    private static final String VALUE  = "noindex, nofollow, noarchive, noimageindex, nosnippet";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader(HEADER, VALUE);
        chain.doFilter(request, response);
    }
}

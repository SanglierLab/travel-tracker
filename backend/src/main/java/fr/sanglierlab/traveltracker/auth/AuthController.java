package fr.sanglierlab.traveltracker.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * La connexion ({@code POST /api/auth/login}) et la déconnexion ({@code POST /api/auth/logout})
 * sont gérées par Spring Security (voir SecurityConfig). Ce contrôleur n'expose que l'état de la session.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication, CsrfToken csrfToken) {
        // Lire le jeton force son écriture dans le cookie XSRF-TOKEN : le front l'appelle au démarrage.
        csrfToken.getToken();
        if (authentication != null && authentication.isAuthenticated()) {
            return Map.of("authenticated", true, "username", authentication.getName());
        }
        return Map.of("authenticated", false);
    }
}

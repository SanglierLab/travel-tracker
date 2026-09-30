package fr.sanglierlab.travel.auth;

import fr.sanglierlab.travel.auth.dto.LoginForm;
import fr.sanglierlab.travel.auth.dto.SessionDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion, déconnexion, état de session — en JSON, sans page de login.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Connexion. Un échec remonte en 401 via le gestionnaire global
     * ({@code BadCredentialsException}).
     */
    @PostMapping("/login")
    public SessionDto login(@Valid @RequestBody LoginForm form,
                            HttpServletRequest request,
                            HttpServletResponse response) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(form.username(), form.password()));

        // Régénère l'identifiant de session : parade à la fixation de session
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        log.info("Connexion réussie : {} depuis {}", authentication.getName(), request.getRemoteAddr());
        return SessionDto.of(authentication.getName());
    }

    /** Déconnexion : invalide la session côté serveur. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /**
     * État courant. Accessible sans authentification : le frontend l'appelle
     * au chargement pour savoir s'il doit afficher le menu d'administration.
     */
    @GetMapping("/me")
    public SessionDto me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return SessionDto.anonymous();
        }
        return SessionDto.of(authentication.getName());
    }
}

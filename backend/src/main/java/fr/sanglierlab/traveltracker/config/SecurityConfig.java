package fr.sanglierlab.traveltracker.config;

import fr.sanglierlab.traveltracker.auth.LoginAttemptService;
import fr.sanglierlab.traveltracker.auth.LoginRateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService attempts) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/actuator/health", "/api/actuator/health/**").permitAll()
                        // Page d'erreur interne de Spring : sans cela, un 400/500 est remplacé par un 401.
                        .requestMatchers("/error").permitAll()
                        // /media est servi par nginx en production ; ici seulement pour le profil dev.
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Tout le reste est fermé par défaut (l'API de positions /api/track/** arrive en phase 4).
                        .anyRequest().denyAll())

                // CSRF : cookie XSRF-TOKEN lisible par le JS, renvoyé dans l'en-tête X-XSRF-TOKEN.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/track/**"))

                // Connexion par formulaire (champs username / password), réponses JSON pour le front.
                .formLogin(form -> form
                        .loginPage("/connexion")
                        .loginProcessingUrl(LoginRateLimitFilter.LOGIN_PATH)
                        .successHandler((request, response, authentication) -> {
                            attempts.recordSuccess(request.getRemoteAddr());
                            json(response, HttpServletResponse.SC_OK, "{\"authenticated\":true}");
                        })
                        .failureHandler((request, response, exception) -> {
                            attempts.recordFailure(request.getRemoteAddr());
                            json(response, HttpServletResponse.SC_UNAUTHORIZED, "{\"error\":\"invalid_credentials\"}");
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_OK))
                        .deleteCookies("JSESSIONID"))

                // API : 401 (et non une redirection) quand la session est absente.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // Non-indexation (en plus de robots.txt, de la balise meta et de l'en-tête nginx).
                .headers(headers -> headers.addHeaderWriter(
                        new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow, noarchive")))

                .addFilterBefore(new LoginRateLimitFilter(attempts), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Comptes admin issus de la configuration serveur (pas de table en base). */
    @Bean
    UserDetailsService userDetailsService(AppProperties props) {
        List<UserDetails> users = props.admins().stream()
                .map(admin -> User.withUsername(admin.username())
                        .password(admin.passwordHash())
                        .roles("ADMIN")
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    private static void json(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}

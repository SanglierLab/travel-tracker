package fr.sanglierlab.travel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.sanglierlab.travel.common.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Deux chaînes de sécurité, deux mécanismes distincts :
 *
 *  1. /api/ingest/**  — jeton porteur, sans session (tracker mobile)
 *  2. tout le reste   — session HTTP pour l'admin, lecture publique libre
 *
 * Aucune page de login générée par Spring : le frontend poste sur
 * /api/auth/login et reçoit du JSON. Une application à écran unique n'a
 * que faire d'une redirection 302 vers un formulaire.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ------------------------------------------------------------------ beans

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Accepte {noop}, {bcrypt}, {argon2}... selon ce qui est écrit en conf
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    // --------------------------------------------------- 1. API d'ingestion

    @Bean
    @Order(1)
    public SecurityFilterChain ingestChain(HttpSecurity http,
                                           AppProperties properties,
                                           ObjectMapper objectMapper) throws Exception {
        return http
                .securityMatcher("/api/ingest/**")
                .csrf(csrf -> csrf.disable())                       // jeton porteur, pas de cookie
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .anonymous(anonymous -> anonymous.disable())
                .addFilterBefore(new IngestTokenFilter(properties.ingestToken()),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole(Roles.INGEST))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonEntryPoint(objectMapper,
                                "Jeton d'ingestion absent ou invalide")))
                .build();
    }

    // ------------------------------------------------ 2. Site public + admin

    @Bean
    @Order(2)
    public SecurityFilterChain appChain(HttpSecurity http,
                                        SecurityContextRepository contextRepository,
                                        ObjectMapper objectMapper) throws Exception {
        return http
                // Protection CSRF assurée par le cookie de session en SameSite=Strict
                // (voir server.servlet.session.cookie.same-site) — cf. docs/DECISIONS.md D8
                .csrf(csrf -> csrf.disable())
                .securityContext(context -> context
                        .securityContextRepository(contextRepository))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // --- consultation libre : c'est le principe du site ---
                        .requestMatchers(HttpMethod.GET,
                                "/api/galleries/**",
                                "/api/trace/**",
                                "/api/trips",
                                "/media/**",
                                "/robots.txt").permitAll()
                        // --- authentification ---
                        .requestMatchers("/api/auth/login", "/api/auth/me").permitAll()
                        // --- administration ---
                        .requestMatchers("/api/admin/**").hasRole(Roles.ADMIN)
                        .requestMatchers("/api/auth/logout").authenticated()
                        // --- le reste (erreurs, actuator absent...) ---
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonEntryPoint(objectMapper,
                                "Authentification requise"))
                        .accessDeniedHandler((request, response, denied) ->
                                writeJson(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                        "Accès refusé", request.getRequestURI())))
                // Mécanismes par défaut dont nous n'avons pas l'usage
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())   // géré par AuthController
                .build();
    }

    // ----------------------------------------------------------------- utils

    private static org.springframework.security.web.AuthenticationEntryPoint jsonEntryPoint(
            ObjectMapper objectMapper, String message) {
        return (request, response, authException) ->
                writeJson(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                        message, request.getRequestURI());
    }

    private static void writeJson(HttpServletResponse response, ObjectMapper objectMapper,
                                  int status, String message, String path) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status, message, path));
    }
}

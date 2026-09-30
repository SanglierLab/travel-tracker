package fr.sanglierlab.travel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Réglages Spring MVC.
 *
 * Pas de configuration CORS : en développement Vite relaie /api vers le
 * backend, en production nginx fait de même. Le navigateur ne voit qu'une
 * seule origine dans les deux cas.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // Emplacement prévu pour d'éventuels convertisseurs (enums, dates).
    }
}

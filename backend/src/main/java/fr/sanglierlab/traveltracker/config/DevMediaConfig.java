package fr.sanglierlab.traveltracker.config;

import fr.sanglierlab.traveltracker.media.StorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Développement uniquement (profil « dev ») : sert /media/** directement depuis le dossier des médias,
 * là où nginx le fait en production. Activer avec  spring.profiles.active: dev  dans backend/config/application.yml.
 */
@Configuration
@Profile("dev")
public class DevMediaConfig implements WebMvcConfigurer {

    private final StorageService storage;

    public DevMediaConfig(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storage.getMediaRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/media/**").addResourceLocations(location);
    }
}

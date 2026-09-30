package fr.sanglierlab.travel.auth;

import fr.sanglierlab.travel.config.AppProperties;
import fr.sanglierlab.travel.config.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Source des comptes administrateurs : le fichier de configuration, rien d'autre.
 *
 * Pas de table « user », pas d'inscription, pas de réinitialisation de mot de
 * passe. Le voyageur est le seul compte, éventuellement deux ou trois.
 *
 * Les mots de passe sont lus au format Spring Security « delegating » :
 *  - « monSecret »            -> préfixé automatiquement en {noop} (clair)
 *  - « {bcrypt}$2a$10$... »   -> utilisé tel quel
 */
@Service
public class ConfigUserDetailsService extends InMemoryUserDetailsManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigUserDetailsService.class);

    public ConfigUserDetailsService(AppProperties properties) {
        super(buildUsers(properties));
        log.info("Comptes administrateurs chargés depuis la configuration : {}",
                properties.accounts().stream().map(AppProperties.AdminAccount::username).toList());
    }

    private static List<UserDetails> buildUsers(AppProperties properties) {
        return properties.accounts().stream()
                .map(account -> (UserDetails) User
                        .withUsername(account.username())
                        .password(normalize(account.password()))
                        .roles(Roles.ADMIN)
                        .build())
                .toList();
    }

    /** Ajoute le préfixe {noop} si aucun encodeur n'est explicitement déclaré. */
    private static String normalize(String password) {
        return password.startsWith("{") ? password : "{noop}" + password;
    }
}

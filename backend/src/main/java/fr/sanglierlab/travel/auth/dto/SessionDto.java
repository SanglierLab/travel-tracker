package fr.sanglierlab.travel.auth.dto;

/**
 * État de session renvoyé au frontend.
 * {@code authenticated = false} pour un visiteur : ce n'est pas une erreur,
 * la consultation est libre.
 */
public record SessionDto(boolean authenticated, String username) {

    public static SessionDto anonymous() {
        return new SessionDto(false, null);
    }

    public static SessionDto of(String username) {
        return new SessionDto(true, username);
    }
}

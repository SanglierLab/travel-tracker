package fr.sanglierlab.travel.config;

/** Rôles applicatifs. Volontairement minimalistes : un admin, un injecteur. */
public final class Roles {

    /** Administrateur authentifié par session (comptes du fichier de conf). */
    public static final String ADMIN = "ADMIN";

    /** Porteur du jeton d'ingestion : n'a accès qu'à /api/ingest/**. */
    public static final String INGEST = "INGEST";

    private Roles() {}
}

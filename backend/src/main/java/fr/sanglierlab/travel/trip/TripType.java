package fr.sanglierlab.travel.trip;

import fr.sanglierlab.travel.trace.TraceSource;

/** Nature d'un trajet suivi automatiquement. */
public enum TripType {

    /** Vol, identifié par son indicatif d'appel. */
    ADSB("indicatif"),

    /** Traversée maritime, identifiée par le MMSI du navire. */
    AIS("MMSI");

    private final String identifierLabel;

    TripType(String identifierLabel) {
        this.identifierLabel = identifierLabel;
    }

    /** Libellé du champ d'identification, affiché dans le formulaire. */
    public String identifierLabel() {
        return identifierLabel;
    }

    public TraceSource traceSource() {
        return this == ADSB ? TraceSource.ADSB : TraceSource.AIS;
    }
}

package fr.sanglierlab.traveltracker.stage;

/** Type d'étape : un lieu où l'on reste, ou un trajet. */
public enum StageKind {
    PLACE, TRAIN, PLANE, BOAT;

    /** Un trajet a une position de début et une de fin, et peut avoir un tracé GPX ; une étape simple n'a qu'une position. */
    public boolean isTrip() {
        return this != PLACE;
    }
}

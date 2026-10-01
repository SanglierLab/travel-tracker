package fr.sanglierlab.traveltracker.common;

/** Corps JSON des réponses d'erreur : {"message": "..."} */
public record ApiError(String message) {
}

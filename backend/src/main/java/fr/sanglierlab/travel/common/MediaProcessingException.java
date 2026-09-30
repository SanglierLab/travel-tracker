package fr.sanglierlab.travel.common;

/** Échec du traitement d'un média (conversion, vignette, écriture) — 500. */
public class MediaProcessingException extends RuntimeException {

    public MediaProcessingException(String message) {
        super(message);
    }

    public MediaProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

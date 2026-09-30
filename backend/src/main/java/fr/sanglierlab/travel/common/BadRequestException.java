package fr.sanglierlab.travel.common;

/** Requête invalide côté métier — traduite en 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

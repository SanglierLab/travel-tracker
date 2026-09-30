package fr.sanglierlab.travel.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * Traduction des exceptions en réponses JSON homogènes.
 * Les messages sont rédigés pour être affichés directement à l'utilisateur.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex,
                                                         HttpServletRequest request) {
        log.warn("Échec de connexion depuis {}", request.getRemoteAddr());
        return build(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), messageOf(error)))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(), "Formulaire invalide",
                request.getRequestURI(), fields));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "Fichier trop volumineux pour être envoyé", request);
    }

    @ExceptionHandler(MediaProcessingException.class)
    public ResponseEntity<ApiError> handleMedia(MediaProcessingException ex,
                                                HttpServletRequest request) {
        log.error("Traitement média en échec sur {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Le média n'a pas pu être traité", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erreur inattendue sur {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur", request);
    }

    // ------------------------------------------------------------------ utils

    private static String messageOf(FieldError error) {
        return error.getDefaultMessage() == null ? "valeur invalide" : error.getDefaultMessage();
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), message, request.getRequestURI()));
    }
}

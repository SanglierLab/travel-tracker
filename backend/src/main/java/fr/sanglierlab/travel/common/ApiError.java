package fr.sanglierlab.travel.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Corps d'erreur unique de l'API. Les messages sont en français :
 * ils sont affichés tels quels par l'interface.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String message,
        String path,
        List<FieldError> errors
) {

    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String message, String path) {
        return new ApiError(Instant.now(), status, message, path, null);
    }

    public static ApiError of(int status, String message, String path, List<FieldError> errors) {
        return new ApiError(Instant.now(), status, message, path,
                errors == null || errors.isEmpty() ? null : errors);
    }
}

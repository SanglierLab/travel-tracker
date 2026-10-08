package fr.sanglierlab.traveltracker.flight;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Enregistrement d'un vol. L'heure (UTC) est facultative : sans elle, le départ est à minuit UTC. */
public record FlightRequest(
        @NotBlank(message = "Le numéro de vol est obligatoire.")
        @Size(max = 20, message = "Le numéro de vol est trop long.")
        String identifier,

        @NotNull(message = "La date est obligatoire.")
        LocalDate date,

        LocalTime time) {
}

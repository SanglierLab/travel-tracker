package fr.sanglierlab.traveltracker.cruise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Enregistrement d'une croisière (ou traversée). L'heure (UTC) est facultative : sans elle, le départ est à minuit UTC. */
public record CruiseRequest(
        @NotBlank(message = "Le MMSI est obligatoire.")
        @Size(max = 20, message = "Le MMSI est trop long.")
        String identifier,

        @NotNull(message = "La date est obligatoire.")
        LocalDate date,

        LocalTime time) {
}

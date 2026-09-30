package fr.sanglierlab.travel.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginForm(
        @NotBlank(message = "L'identifiant est obligatoire") String username,
        @NotBlank(message = "Le mot de passe est obligatoire") String password
) {}

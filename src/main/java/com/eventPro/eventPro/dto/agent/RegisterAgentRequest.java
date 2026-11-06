package com.eventPro.eventPro.dto.agent;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAgentRequest(

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100)
        String name,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Email invalide")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, message = "8 caracteres minimum")
        String password

) {
    public RegisterAgentRequest {
        email = email == null ? null : email.strip().toLowerCase();
    }
}

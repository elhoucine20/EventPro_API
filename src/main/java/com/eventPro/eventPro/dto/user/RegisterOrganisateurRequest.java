package com.eventPro.eventPro.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterOrganisateurRequest(

        @NotBlank(message = "le nom est oblegatoire")
        @Size(max = 100)
        String name,

        @NotBlank(message = "l'email est oblegatoire")
        @Email(message = "email invalide")
        String email,

        @NotBlank(message = "le mot de pass est oblegatoire")
        @Size(min = 8,message = "8 caracteres minimun")
        String password,

        @NotBlank(message = "le nom de l'organisation est oblegatoire")
        @Size(max = 150)
        String nomOrganisation
) {
    public RegisterOrganisateurRequest {   // email toujours to lowercase et sans espace
        email = email == null ? null : email.strip().toLowerCase();
    }
}

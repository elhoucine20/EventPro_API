package com.eventPro.eventPro.dto.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventRequest(

        @NotBlank(message = "le titre est oblegatoir")
        @Size(max = 150 , message = "150 caracteres maximun")
        String titre,

        @NotBlank(message = "La description est obligatoire")
        @Size(max = 1000, message = "1000 caracteres maximum")
        String description,

        @NotBlank(message = "Le lieu est obligatoire")
        @Size(max = 255, message = "255 caracteres maximum")
        String lieu,

        @NotNull(message = "La date de debut est obligatoire")
        LocalDateTime dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDateTime dateFin,

        @NotNull(message = "La date d'ouverture de vente est obligatoire")
        LocalDateTime dateOuvertureVente,

        @NotNull(message = "La date de clôture de vente est obligatoire")
        LocalDateTime dateClotureVente,

        @NotNull(message = "L'organisateur est obligatoire")
        UUID organisateurId
) {
}

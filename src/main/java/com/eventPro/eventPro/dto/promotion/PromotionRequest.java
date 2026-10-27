package com.eventPro.eventPro.dto.promotion;

import com.eventPro.eventPro.model.enums.TypeReduction;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PromotionRequest(

        @NotBlank(message = "le code est obligatoire")
        @Size(max = 50,message = "50 caracteres maximum")
        @Pattern(regexp = "^[A-Z0-9_-]+$",message = "le code ne peut contenir que des letters,chiffres,_ et -")
        String code,

        @NotNull(message = "le type de reduction esr obligatoire")
        TypeReduction typeReduction,

        @NotNull(message = "la valeur est obligatoire")
        @DecimalMin(value = "0.01",message = "La valeur doit etre positive")
        @Digits(integer = 8, fraction = 2, message = "Valeur invalide (8 chiffres et 2 decimales maximum)")
        BigDecimal valeurReduction,

        @NotNull(message = "La date de debut est obligatoire")
        LocalDate dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDate dateFin,

        @NotNull(message = "Le nombre maximum d'utilisations est obligatoire")
        @Min(value = 1, message = "Au moins 1 utilisation")
        Integer maxUtilisations,

        // TEMPORAIRE : sera remplacé par l'utilisateur connecté (Spring Security)
        @NotNull(message = "L'organisateur est obligatoire")
        UUID organisateurId
) {
    public PromotionRequest{
        code = code == null ? null : code.strip().toUpperCase();
    }
}

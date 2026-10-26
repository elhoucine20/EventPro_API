package com.eventPro.eventPro.dto.categorie;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CategorieBilletRequest(

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 100,message = "100 caracteres maximum")
        String nom,

        @NotNull(message = "le prix est obligatoire")
        @DecimalMin(value = "0.00",message = "le prix ne peut pas etre negatif")
        @Digits(integer = 8,fraction = 2,message = "le prix invalide (8 chifres et 2 decimales maximum)")
        BigDecimal prixUnitaire,

        @NotNull(message = "la qauntite est obligatoire")
        @Min(value = 1,message = "la qauntite doit etre au moins 1")
        Integer quantiteTotale,

        @NotNull(message = "le quota par client est obligatoire")
        @Min(value = 1,message = "la quota doit etre au moins 1")
        Integer quotaMaxParClient,

        @NotNull(message = "l'organisateur est obligatoire")
        UUID organisateurId
) {
}

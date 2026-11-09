package com.eventPro.eventPro.dto.categorie;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CategorieBilletCreation(

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "100 caractères maximum")
        String nom,

        @NotNull(message = "Le prix est obligatoire")
        @DecimalMin(value = "0.00", message = "Le prix ne peut pas être négatif")
        @Digits(integer = 8, fraction = 2, message = "Prix invalide")
        BigDecimal prixUnitaire,

        @NotNull(message = "La quantité est obligatoire")
        @Min(value = 1, message = "La quantité doit être au moins 1")
        Integer quantiteTotale,

        @NotNull(message = "Le quota par client est obligatoire")
        @Min(value = 1, message = "Le quota doit être au moins 1")
        Integer quotaMaxParClient

) {
}

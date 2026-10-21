package com.eventPro.eventPro.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterClientRequest(

        @NotBlank(message = "le nom est oblegatoire")
        @Size(max = 100)
        String name,

        @NotBlank(message = "l'email est oblegatoire")
        @Email(message = "email invalide")
        String email,

        @NotBlank(message = "le mot de pass est oblegatoire")
        @Size(min = 8,message = "8 caracteres minimun")
        String password,

        @NotBlank(message = "le telephone esr oblegatoire")
        @Pattern(regexp = "^\\+?[0-9]{8,15}$",message = "telephone invalide")
        String phone
) {
        public RegisterClientRequest {  //
                email = email == null ? null : email.strip().toLowerCase();
        }
}

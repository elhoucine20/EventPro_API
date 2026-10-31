package com.eventPro.eventPro.paiement;

       //c'est la reponse de bank
public record PaymentResult( // est-ce que le paiemnt pass avec succees ou ne pass pas


        boolean success,
        String message
) {
}

package com.eventPro.eventPro.paiement;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public interface PaymentGateway {  // inteface => service indique  charge paiemnt sans connuu la bank (class)

    // simulateFailure : utilise uniquement par FakePaymentGateway, ignore par un vrai fournisseur
    PaymentResult charge(BigDecimal montant,String reference,boolean simulateFailure);
}

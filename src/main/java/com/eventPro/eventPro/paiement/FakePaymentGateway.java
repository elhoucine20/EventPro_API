package com.eventPro.eventPro.paiement;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FakePaymentGateway implements PaymentGateway{


    @Override
    public PaymentResult charge(BigDecimal montant, String reference, boolean simulateFailure) {

        if (simulateFailure){
            return new PaymentResult(false,"Paiement refuse (simulation)");
        }
        return new PaymentResult(true, "Paiement accepte (simulation)");
    }
}

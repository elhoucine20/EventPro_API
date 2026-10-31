package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.paiement.PaiementRequest;
import com.eventPro.eventPro.dto.paiement.PaiementResponse;
import com.eventPro.eventPro.service.PaiementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaiementController {

    private final PaiementService paiementService;

    @PostMapping("/reservations/{id}/paiement")
    public PaiementResponse payer(@PathVariable UUID id,
                                  @Valid @RequestBody PaiementRequest request) {

        //System.out.println("this is POST method");
        return paiementService.payer(id, request);
    }

    // TEMPORAIRE : clientId sera remplace par l'utilisateur connecte
    @GetMapping("/reservations/{id}/paiement")
    public PaiementResponse getByReservation(@PathVariable UUID id,
                                             @RequestParam UUID clientId) {
        return paiementService.getByReservation(id, clientId);
    }

}

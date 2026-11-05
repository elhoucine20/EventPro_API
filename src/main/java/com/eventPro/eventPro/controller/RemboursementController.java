package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.remboursement.RemboursementRequest;
import com.eventPro.eventPro.dto.remboursement.RemboursementResponse;
import com.eventPro.eventPro.service.RemboursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RemboursementController {

    private final RemboursementService remboursementService;

    @PostMapping("/reservations/{id}/remboursements")
    @ResponseStatus(HttpStatus.CREATED)
    public RemboursementResponse demander(@PathVariable UUID id,
                                          @Valid @RequestBody RemboursementRequest request) {
        return remboursementService.demander(id, request);
    }

    // TEMPORAIRE : clientId sera remplacé par l'utilisateur connecté
    @GetMapping("/reservations/{id}/remboursements")
    public List<RemboursementResponse> getByReservation(@PathVariable UUID id,
                                                        @RequestParam UUID clientId) {
        return remboursementService.getByReservation(id, clientId);
    }
}
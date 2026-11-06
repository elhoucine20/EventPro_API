package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.ticket.CheckInRequest;
import com.eventPro.eventPro.dto.ticket.VerificationResponse;
import com.eventPro.eventPro.service.CheckInService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    // TEMPORAIRE : agentId sera remplacé par l'utilisateur connecté
    @GetMapping("/billets/{code}/verification")
    public VerificationResponse verifier(@PathVariable String code, @RequestParam UUID agentId) {
        return checkInService.verifier(code, agentId);
    }

    @PostMapping("/checkin")
    public VerificationResponse composter(@Valid @RequestBody CheckInRequest request) {
        return checkInService.composter(request);
    }
}
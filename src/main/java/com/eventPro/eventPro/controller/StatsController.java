package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.stats.EventStatsResponse;
import com.eventPro.eventPro.dto.stats.OrganisateurStatsResponse;
import com.eventPro.eventPro.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    // TEMPORAIRE : l'identité de l'organisateur sera celle de l'utilisateur connecté
    @GetMapping("/organisateurs/{organisateurId}/stats")
    public OrganisateurStatsResponse statsOrganisateur(@PathVariable UUID organisateurId) {
        return statsService.statsOrganisateur(organisateurId);
    }

    @GetMapping("/events/{eventId}/stats")
    public EventStatsResponse statsEvent(@PathVariable UUID eventId, @RequestParam UUID organisateurId) {
        return statsService.statsEvent(eventId, organisateurId);
    }
}
package com.eventPro.eventPro.controller;

import com.eventPro.eventPro.dto.promotion.PromotionRequest;
import com.eventPro.eventPro.dto.promotion.PromotionResponse;
import com.eventPro.eventPro.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping("/events/{eventId}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponse create(@PathVariable UUID eventId,
                                    @Valid @RequestBody PromotionRequest request) {
        return promotionService.create(eventId, request);
    }

    // TEMPORAIRE : organisateurId sera remplace par l'utilisateur connecte
    @GetMapping("/events/{eventId}/promotions")
    public List<PromotionResponse> listByEvent(@PathVariable UUID eventId,
                                               @RequestParam UUID organisateurId) {
        return promotionService.listByEvent(eventId, organisateurId);
    }

    @PutMapping("/promotions/{id}")
    public PromotionResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody PromotionRequest request) {
        return promotionService.update(id, request);
    }

    @DeleteMapping("/promotions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam UUID organisateurId) {
        promotionService.delete(id, organisateurId);
    }
}
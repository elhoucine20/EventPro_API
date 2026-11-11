package com.eventPro.eventPro.service;

import com.eventPro.eventPro.model.CategorieBillet;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.LigneReservationRepository;
import com.eventPro.eventPro.repository.projection.CleQuantite;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DisponibiliteService {

    private final LigneReservationRepository ligneReservationRepository;
    private final CategorieBilletRepository categorieBilletRepository;

    @Transactional(readOnly = true)
    public Map<UUID, Integer> placesParCategorie(UUID eventId, Collection<CategorieBillet> categories) {
        Map<UUID, Long> prises = toMap(ligneReservationRepository.sumQuantitesActivesParCategorie(eventId, LocalDateTime.now()));

        Map<UUID, Integer> places = new HashMap<>();
        for (CategorieBillet categorie : categories) {
            long restant = categorie.getQuantiteTotale() - prises.getOrDefault(categorie.getId(), 0L);
            places.put(categorie.getId(), (int) Math.max(restant, 0));
        }
        return places;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> placesParEvent(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> totaux = toMap(categorieBilletRepository.sumQuantiteTotaleParEvent(eventIds));
        Map<UUID, Long> prises = toMap(ligneReservationRepository.sumQuantitesActivesParEvent(eventIds, LocalDateTime.now()));

        Map<UUID, Integer> places = new HashMap<>();
        for (UUID eventId : eventIds) {
            long restant = totaux.getOrDefault(eventId, 0L) - prises.getOrDefault(eventId, 0L);
            places.put(eventId, (int) Math.max(restant, 0));
        }
        return places;
    }

    private Map<UUID, Long> toMap(List<CleQuantite> lignes) {
        return lignes.stream().collect(Collectors.toMap(CleQuantite::cle, CleQuantite::quantite));
    }
}
package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.stats.CategorieStats;
import com.eventPro.eventPro.dto.stats.EventStatsResponse;
import com.eventPro.eventPro.dto.stats.OrganisateurStatsResponse;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.EventRepository;
import com.eventPro.eventPro.repository.LigneReservationRepository;
import com.eventPro.eventPro.repository.OrganisateurRepository;
import com.eventPro.eventPro.repository.RemboursementRepository;
import com.eventPro.eventPro.repository.ReservationRepository;
import com.eventPro.eventPro.repository.projection.CleMontant;
import com.eventPro.eventPro.repository.projection.CleQuantite;
import com.eventPro.eventPro.repository.projection.CleQuantiteMontant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final EventRepository eventRepository;
    private final OrganisateurRepository organisateurRepository;
    private final CategorieBilletRepository categorieBilletRepository;
    private final ReservationRepository reservationRepository;
    private final LigneReservationRepository ligneReservationRepository;
    private final RemboursementRepository remboursementRepository;

    @Transactional(readOnly = true)
    public OrganisateurStatsResponse statsOrganisateur(UUID organisateurId) {
        Organisateur organisateur = organisateurRepository.findById(organisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Organisateur introuvable : " + organisateurId));
        List<EventStatsResponse> events = construireStats(eventRepository.findAllByOrganisateurId(organisateurId));

        long totalBillets = events.stream().mapToLong(EventStatsResponse::billetsVendus).sum();
        BigDecimal totalCa = events.stream()
                .map(EventStatsResponse::chiffreAffaires)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRembourse = events.stream()
                .map(EventStatsResponse::montantRembourse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrganisateurStatsResponse(organisateur.getId(), organisateur.getNomOrganisation(),
                totalBillets, totalCa, totalRembourse, events);
    }

    @Transactional(readOnly = true)
    public EventStatsResponse statsEvent(UUID eventId, UUID organisateurId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + eventId));
        if (!event.getOrganisateur().getId().equals(organisateurId)) {
            throw new ForbiddenOperationException("Vous n'êtes pas l'organisateur de cet événement");
        }

        EventStatsResponse base = construireStats(List.of(event)).get(0);

        Map<UUID, CleQuantiteMontant> ventes = indexerQuantiteMontant(
                ligneReservationRepository.sumVentesParCategorie(eventId));
        List<CategorieStats> categories = categorieBilletRepository.findByEventId(eventId).stream()
                .map(c -> {
                    CleQuantiteMontant v = ventes.get(c.getId());
                    return new CategorieStats(c.getId(), c.getNom(), Math.toIntExact(c.getQuantiteTotale()),
                            v == null ? 0L : v.quantite(),
                            v == null ? BigDecimal.ZERO : v.montant());
                })
                .toList();

        return new EventStatsResponse(base.eventId(), base.titre(), base.statut(),
                base.reservationsConfirmees(), base.billetsVendus(), base.capaciteTotale(),
                base.tauxRemplissage(), base.chiffreAffaires(), base.montantRembourse(), categories);
    }

    // ---------- helpers ----------
    private List<EventStatsResponse> construireStats(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = events.stream().map(Event::getId).toList();

        Map<UUID, CleQuantiteMontant> ventes = indexerQuantiteMontant(reservationRepository.statsVentesParEvent(ids));
        Map<UUID, Long> billets = indexerQuantite(ligneReservationRepository.sumBilletsVendusParEvent(ids));
        Map<UUID, Long> capacites = indexerQuantite(categorieBilletRepository.sumQuantiteTotaleParEvent(ids));
        Map<UUID, BigDecimal> rembourses = indexerMontant(remboursementRepository.sumMontantApprouveParEvent(ids));

        return events.stream().map(event -> {
            CleQuantiteMontant vente = ventes.get(event.getId());
            long vendus = billets.getOrDefault(event.getId(), 0L);
            long capacite = capacites.getOrDefault(event.getId(), 0L);
            return new EventStatsResponse(
                    event.getId(),
                    event.getTitre(),
                    event.getStatut(),
                    vente == null ? 0L : vente.quantite(),
                    vendus,
                    capacite,
                    taux(vendus, capacite),
                    vente == null ? BigDecimal.ZERO : vente.montant(),
                    rembourses.getOrDefault(event.getId(), BigDecimal.ZERO),
                    List.of());
        }).toList();
    }

    private BigDecimal taux(long vendus, long capacite) {
        if (capacite == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(vendus).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(capacite), 1, RoundingMode.HALF_UP);
    }

    private Map<UUID, CleQuantiteMontant> indexerQuantiteMontant(List<CleQuantiteMontant> lignes) {
        return lignes.stream().collect(Collectors.toMap(CleQuantiteMontant::cle, l -> l));
    }

    private Map<UUID, Long> indexerQuantite(List<CleQuantite> lignes) {
        return lignes.stream().collect(Collectors.toMap(CleQuantite::cle, CleQuantite::quantite));
    }

    private Map<UUID, BigDecimal> indexerMontant(List<CleMontant> lignes) {
        return lignes.stream().collect(Collectors.toMap(CleMontant::cle, CleMontant::montant));
    }
}
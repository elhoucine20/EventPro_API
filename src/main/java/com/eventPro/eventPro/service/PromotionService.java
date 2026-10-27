package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.promotion.PromotionRequest;
import com.eventPro.eventPro.dto.promotion.PromotionResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.promotion.PromotionMapper;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Promotion;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.TypeReduction;
import com.eventPro.eventPro.repository.EventRepository;
import com.eventPro.eventPro.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final EventRepository eventRepository;
    private final PromotionMapper promotionMapper;

    @Transactional
    public PromotionResponse create(UUID eventId, PromotionRequest request) {
        Event event = findEvent(eventId);
        checkOwner(event, request.organisateurId());
        checkEventActive(event);
        validate(request);
        checkCodeUnique(eventId, request.code());

        Promotion promotion = promotionMapper.toEntity(request);
        promotion.setEvent(event);
        return promotionMapper.toResponse(promotionRepository.save(promotion));
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listByEvent(UUID eventId, UUID organisateurId) {
        Event event = findEvent(eventId);
        checkOwner(event, organisateurId);
        return promotionRepository.findByEventId(eventId).stream()
                .map(promotionMapper::toResponse)
                .toList();
    }

    @Transactional
    public PromotionResponse update(UUID id, PromotionRequest request) {
        Promotion promotion = findPromotion(id);
        Event event = promotion.getEvent();
        checkOwner(event, request.organisateurId());
        checkEventActive(event);
        validate(request);

        if (request.maxUtilisations() < promotion.getNombreUtilisations()) {
            throw new BusinessRuleException("Le maximum d'utilisations ne peut pas etre inferieur au nombre deja utilise");
        }
        if (!promotion.getCode().equals(request.code())) {
            if (promotion.getNombreUtilisations() > 0) {
                throw new BusinessRuleException("Le code ne peut plus etre modifie : la promotion a deja ete utilisee");
            }
            checkCodeUnique(event.getId(), request.code());
        }

        promotionMapper.updateEntity(request, promotion);
        return promotionMapper.toResponse(promotion);
    }

    @Transactional
    public void delete(UUID id, UUID organisateurId) {
        Promotion promotion = findPromotion(id);
        checkOwner(promotion.getEvent(), organisateurId);
        if (promotion.getNombreUtilisations() > 0) {
            throw new BusinessRuleException("Impossible de supprimer une promotion deja utilisee");
        }
        // TODO :refuser aussi si une reservation reference cette promotion
        promotionRepository.delete(promotion);
    }

    // ---------- helpers ----------

    private Event findEvent(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evenement introuvable : " + id));
    }

    private Promotion findPromotion(UUID id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion introuvable : " + id));
    }

    private void checkOwner(Event event, UUID organisateurId) {
        if (!event.getOrganisateur().getId().equals(organisateurId)) {
            throw new ForbiddenOperationException("Vous n'etes pas l'organisateur de cet evenement");
        }
    }

    private void checkEventActive(Event event) {
        if (event.getStatut() == StatusEvenment.CANCELLED || event.getStatut() == StatusEvenment.COMPLETED) {
            throw new BusinessRuleException("Impossible de gerer les promotions d'un evenement annule ou termine");
        }
    }

    private void validate(PromotionRequest request) {
        if (request.dateFin().isBefore(request.dateDebut())) {
            throw new BusinessRuleException("La date de fin doit etre apres la date de debut");
        }
        if (request.dateFin().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("La date de fin est deja passee");
        }
        if (request.typeReduction() == TypeReduction.PERCENTAGE
                && request.valeurReduction().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException("Un pourcentage ne peut pas depasser 100");
        }
    }

    private void checkCodeUnique(UUID eventId, String code) {
        if (promotionRepository.existsByEventIdAndCode(eventId, code)) {
            throw new BusinessRuleException("Le code \"" + code + "\" existe deja pour cet avanement");
        }
    }
}
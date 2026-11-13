package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.promotion.PromotionRequest;
import com.eventPro.eventPro.dto.promotion.PromotionResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.promotion.PromotionMapper;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.model.Promotion;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.model.enums.TypeReduction;
import com.eventPro.eventPro.repository.EventRepository;
import com.eventPro.eventPro.repository.PromotionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT) // pas d'erreur si un stub n'est pas utilise dans un test
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private PromotionMapper promotionMapper;

    @InjectMocks
    private PromotionService service;

    private final UUID eventId = UUID.randomUUID();
    private final UUID orgaId = UUID.randomUUID();
    private final UUID promoId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    // ---------- petits helpers (comme OneUser() avant) ----------
    private Event OneEvent(StatusEvenment statut, UUID ownerId) {
        Organisateur orga = Organisateur.builder().id(ownerId).build();
        return Event.builder().id(eventId).statut(statut).organisateur(orga).build();
    }

    private Promotion OnePromotion(Event event, String code, int utilisations) {
        return Promotion.builder().id(promoId).code(code).event(event).nombreUtilisations(utilisations).build();
    }

    private PromotionRequest OneRequest(String code, TypeReduction type, String valeur,
                                        LocalDate debut, LocalDate fin, int max, UUID owner) {
        PromotionRequest r = mock(PromotionRequest.class);
        when(r.code()).thenReturn(code);
        when(r.typeReduction()).thenReturn(type);
        when(r.valeurReduction()).thenReturn(new BigDecimal(valeur));
        when(r.dateDebut()).thenReturn(debut);
        when(r.dateFin()).thenReturn(fin);
        when(r.maxUtilisations()).thenReturn(max);
        when(r.organisateurId()).thenReturn(owner);
        return r;
    }

    // requete valide : TEST10, 10%, du jour -1 a +25, max 100
    private PromotionRequest RequestValide() {
        return OneRequest("TEST10", TypeReduction.PERCENTAGE, "10", today.minusDays(1), today.plusDays(25), 100, orgaId);
    }

    // =====================================================================
    // create
    // =====================================================================
    @Test
    void create_pourcentageValide_lieLaPromotionALEvent() {
        createNominal(RequestValide());
    }

    @Test
    void create_montantFixe_valeurSuperieureA100_estAutorisee() {
        // 500 MAD de reduction : la limite de 100 ne concerne que les pourcentages
        createNominal(OneRequest("FIX500", TypeReduction.FIXED_AMOUNT, "500", today, today.plusDays(10), 50, orgaId));
    }

    @Test
    void create_pourcentageExactement100_estAutorise() {
        createNominal(OneRequest("FREE", TypeReduction.PERCENTAGE, "100", today, today.plusDays(10), 5, orgaId));
    }

    @Test
    void create_dateFinEgaleAujourdhui_estAutorisee() {
        createNominal(OneRequest("TODAY", TypeReduction.PERCENTAGE, "10", today, today, 5, orgaId));
    }

    @Test
    void create_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.create(eventId, RequestValide()));

        assertTrue(ex.getMessage().contains(eventId.toString()));
        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, UUID.randomUUID()); // un autre organisateur
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ForbiddenOperationException.class, () -> service.create(eventId, RequestValide()));

        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_eventCancelled_BusinessRule() {
        Event event = OneEvent(StatusEvenment.CANCELLED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, RequestValide()));

        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_eventCompleted_BusinessRule() {
        Event event = OneEvent(StatusEvenment.COMPLETED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, RequestValide()));

        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_dateFinAvantDateDebut_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        PromotionRequest request = OneRequest("X", TypeReduction.PERCENTAGE, "10", today.plusDays(5), today.plusDays(2), 10, orgaId);

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, request));

        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_dateFinDejaPassee_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        // debut < fin, mais fin = hier
        PromotionRequest request = OneRequest("X", TypeReduction.PERCENTAGE, "10", today.minusDays(5), today.minusDays(1), 10, orgaId);

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, request));

        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_pourcentageSuperieurA100_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        PromotionRequest request = OneRequest("X", TypeReduction.PERCENTAGE, "100.01", today, today.plusDays(5), 10, orgaId);

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, request));

        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_codeDejaUtiliseSurCetEvent_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(promotionRepository.existsByEventIdAndCode(eventId, "TEST10")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, RequestValide()));

        verify(promotionRepository, never()).save(any());
    }

    @Test
    void create_ordreDesVerifications_proprietaireAvantStatut() {
        // event annule ET mauvais organisateur : c'est Forbidden qui doit sortir en premier
        Event event = OneEvent(StatusEvenment.CANCELLED, UUID.randomUUID());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ForbiddenOperationException.class, () -> service.create(eventId, RequestValide()));
    }

    // =====================================================================
    // listByEvent
    // =====================================================================
    @Test
    void listByEvent_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.listByEvent(eventId, orgaId));
    }

    @Test
    void listByEvent_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, UUID.randomUUID());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ForbiddenOperationException.class, () -> service.listByEvent(eventId, orgaId));

        verify(promotionRepository, never()).findByEventId(any());
    }

    @Test
    void listByEvent_nominal_retourneToutesLesPromotions() {
        // ARRANGE
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        Promotion p1 = OnePromotion(event, "A", 0);
        Promotion p2 = OnePromotion(event, "B", 2);
        PromotionResponse r1 = mock(PromotionResponse.class);
        PromotionResponse r2 = mock(PromotionResponse.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(promotionRepository.findByEventId(eventId)).thenReturn(List.of(p1, p2));
        when(promotionMapper.toResponse(p1)).thenReturn(r1);
        when(promotionMapper.toResponse(p2)).thenReturn(r2);

        // ACT
        List<PromotionResponse> result = service.listByEvent(eventId, orgaId);

        // ASSERT
        assertEquals(2, result.size());
        assertSame(r1, result.get(0));
        assertSame(r2, result.get(1));
    }

    @Test
    void listByEvent_aucunePromotion_listeVide() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(promotionRepository.findByEventId(eventId)).thenReturn(List.of());

        assertTrue(service.listByEvent(eventId, orgaId).isEmpty());
    }

    @Test
    void listByEvent_eventAnnule_resteConsultablePourLOrganisateur() {
        // la consultation ne verifie pas le statut de l'event
        Event event = OneEvent(StatusEvenment.CANCELLED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(promotionRepository.findByEventId(eventId)).thenReturn(List.of());

        assertTrue(service.listByEvent(eventId, orgaId).isEmpty());
    }

    // =====================================================================
    // update
    // =====================================================================
    @Test
    void update_promotionIntrouvable_ResourceNotFound() {
        when(promotionRepository.findById(promoId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.update(promoId, RequestValide()));

        assertTrue(ex.getMessage().contains(promoId.toString()));
    }

    @Test
    void update_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, UUID.randomUUID());
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "TEST10", 0)));

        assertThrows(ForbiddenOperationException.class, () -> service.update(promoId, RequestValide()));

        verify(promotionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_eventCancelled_BusinessRule() {
        Event event = OneEvent(StatusEvenment.CANCELLED, orgaId);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "TEST10", 0)));

        assertThrows(BusinessRuleException.class, () -> service.update(promoId, RequestValide()));

        verify(promotionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_eventCompleted_BusinessRule() {
        Event event = OneEvent(StatusEvenment.COMPLETED, orgaId);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "TEST10", 0)));

        assertThrows(BusinessRuleException.class, () -> service.update(promoId, RequestValide()));

        verify(promotionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_pourcentageSuperieurA100_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "TEST10", 0)));
        PromotionRequest request = OneRequest("TEST10", TypeReduction.PERCENTAGE, "150", today, today.plusDays(5), 100, orgaId);

        assertThrows(BusinessRuleException.class, () -> service.update(promoId, request));

        verify(promotionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_maxInferieurAuNombreDejaUtilise_BusinessRule() {
        // deja utilisee 5 fois, on veut max = 3
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "TEST10", 5)));
        PromotionRequest request = OneRequest("TEST10", TypeReduction.PERCENTAGE, "10", today, today.plusDays(5), 3, orgaId);

        assertThrows(BusinessRuleException.class, () -> service.update(promoId, request));

        verify(promotionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_maxEgalAuNombreDejaUtilise_estAutorise() {
        // 5 utilisations, max = 5 : accepte (la regle est "inferieur strict")
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        Promotion promotion = OnePromotion(event, "TEST10", 5);
        PromotionRequest request = OneRequest("TEST10", TypeReduction.PERCENTAGE, "10", today, today.plusDays(5), 5, orgaId);
        PromotionResponse response = mock(PromotionResponse.class);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion));
        when(promotionMapper.toResponse(promotion)).thenReturn(response);

        PromotionResponse result = service.update(promoId, request);

        assertSame(response, result);
        verify(promotionMapper).updateEntity(request, promotion);
    }

    @Test
    void update_codeChangeSurPromotionDejaUtilisee_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "OLD", 2)));
        PromotionRequest request = OneRequest("NEW", TypeReduction.PERCENTAGE, "10", today, today.plusDays(5), 100, orgaId);

        assertThrows(BusinessRuleException.class, () -> service.update(promoId, request));

        verify(promotionRepository, never()).existsByEventIdAndCode(any(), anyString()); // refuse avant l'unicite
        verify(promotionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_codeChangeVersUnCodeDejaPris_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "OLD", 0)));
        when(promotionRepository.existsByEventIdAndCode(eventId, "NEW")).thenReturn(true);
        PromotionRequest request = OneRequest("NEW", TypeReduction.PERCENTAGE, "10", today, today.plusDays(5), 100, orgaId);

        assertThrows(BusinessRuleException.class, () -> service.update(promoId, request));

        verify(promotionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_codeChangeEtLibre_metAJour() {
        // ARRANGE : jamais utilisee, nouveau code libre
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        Promotion promotion = OnePromotion(event, "OLD", 0);
        PromotionRequest request = OneRequest("NEW", TypeReduction.PERCENTAGE, "10", today, today.plusDays(5), 100, orgaId);
        PromotionResponse response = mock(PromotionResponse.class);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion));
        when(promotionRepository.existsByEventIdAndCode(eventId, "NEW")).thenReturn(false);
        when(promotionMapper.toResponse(promotion)).thenReturn(response);

        // ACT
        PromotionResponse result = service.update(promoId, request);

        // ASSERT
        assertSame(response, result);
        verify(promotionMapper).updateEntity(request, promotion);
    }

    @Test
    void update_memeCodeSurPromotionUtilisee_metAJourSansVerifierLUnicite() {
        // le code ne change pas : meme avec 3 utilisations, la modification passe
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        Promotion promotion = OnePromotion(event, "TEST10", 3);
        PromotionRequest request = RequestValide();
        PromotionResponse response = mock(PromotionResponse.class);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion));
        when(promotionMapper.toResponse(promotion)).thenReturn(response);

        PromotionResponse result = service.update(promoId, request);

        assertSame(response, result);
        verify(promotionRepository, never()).existsByEventIdAndCode(any(), anyString());
        verify(promotionMapper).updateEntity(request, promotion);
    }

    // =====================================================================
    // delete
    // =====================================================================
    @Test
    void delete_promotionIntrouvable_ResourceNotFound() {
        when(promotionRepository.findById(promoId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(promoId, orgaId));

        verify(promotionRepository, never()).delete(any());
    }

    @Test
    void delete_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, UUID.randomUUID());
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "TEST10", 0)));

        assertThrows(ForbiddenOperationException.class, () -> service.delete(promoId, orgaId));

        verify(promotionRepository, never()).delete(any());
    }

    @Test
    void delete_promotionDejaUtilisee_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(OnePromotion(event, "TEST10", 1)));

        assertThrows(BusinessRuleException.class, () -> service.delete(promoId, orgaId));

        verify(promotionRepository, never()).delete(any());
    }

    @Test
    void delete_promotionJamaisUtilisee_supprime() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        Promotion promotion = OnePromotion(event, "TEST10", 0);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion));

        service.delete(promoId, orgaId);

        verify(promotionRepository).delete(promotion);
    }

    @Test
    void delete_eventAnnule_laSuppressionRestePossible() {
        // delete ne verifie pas le statut de l'event (seulement proprietaire + utilisations)
        Event event = OneEvent(StatusEvenment.CANCELLED, orgaId);
        Promotion promotion = OnePromotion(event, "TEST10", 0);
        when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion));

        service.delete(promoId, orgaId);

        verify(promotionRepository).delete(promotion);
    }

    // =====================================================================
    // methode utilitaire (evite de repeter le meme code dans plusieurs tests)
    // =====================================================================
    private void createNominal(PromotionRequest request) {
        // ARRANGE
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        Promotion promotion = Promotion.builder().code(request.code()).build(); // ce que renvoie le mapper
        PromotionResponse response = mock(PromotionResponse.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(promotionRepository.existsByEventIdAndCode(eventId, request.code())).thenReturn(false);
        when(promotionMapper.toEntity(request)).thenReturn(promotion);
        when(promotionRepository.save(promotion)).thenReturn(promotion);
        when(promotionMapper.toResponse(promotion)).thenReturn(response);

        // ACT
        PromotionResponse result = service.create(eventId, request);

        // ASSERT
        assertSame(response, result);
        assertSame(event, promotion.getEvent()); // la promotion est liee a l'event
    }
}
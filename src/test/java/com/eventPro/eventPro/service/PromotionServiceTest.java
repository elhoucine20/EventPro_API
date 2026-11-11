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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private PromotionRepository promotionRepository;
    @Mock private EventRepository eventRepository;
    @Mock private PromotionMapper promotionMapper;

    @InjectMocks private PromotionService service;

    private final UUID eventId = UUID.randomUUID();
    private final UUID orgaId = UUID.randomUUID();
    private final UUID promoId = UUID.randomUUID();

    private final LocalDate today = LocalDate.now();

    // ================= helpers =================

    private PromotionRequest request(String code, TypeReduction type, String valeur,
                                     LocalDate debut, LocalDate fin, int max, UUID owner) {
        PromotionRequest r = mock(PromotionRequest.class);
        lenient().when(r.code()).thenReturn(code);
        lenient().when(r.typeReduction()).thenReturn(type);
        lenient().when(r.valeurReduction()).thenReturn(new BigDecimal(valeur));
        lenient().when(r.dateDebut()).thenReturn(debut);
        lenient().when(r.dateFin()).thenReturn(fin);
        lenient().when(r.maxUtilisations()).thenReturn(max);
        lenient().when(r.organisateurId()).thenReturn(owner);
        return r;
    }

    /** Requete valide : TEST10, 10%, du jour -1 a +25, max 100. */
    private PromotionRequest requestValide() {
        return request("TEST10", TypeReduction.PERCENTAGE, "10", today.minusDays(1), today.plusDays(25), 100, orgaId);
    }

    private Event event(StatusEvenment statut, UUID ownerId) {
        Organisateur orga = Organisateur.builder().id(ownerId).build();
        return Event.builder().id(eventId).statut(statut).organisateur(orga).build();
    }

    private Promotion promotion(Event event, String code, int utilisations) {
        return Promotion.builder().id(promoId).code(code).event(event).nombreUtilisations(utilisations).build();
    }

    /** Execute un create qui doit reussir et verifie le branchement mapper/save/setEvent. */
    private void creerAvecSucces(PromotionRequest request) {
        Event event = event(StatusEvenment.PUBLISHED, orgaId);
        Promotion promotion = mock(Promotion.class);
        PromotionResponse response = mock(PromotionResponse.class);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(promotionRepository.existsByEventIdAndCode(eventId, request.code())).thenReturn(false);
        when(promotionMapper.toEntity(request)).thenReturn(promotion);
        when(promotionRepository.save(promotion)).thenReturn(promotion);
        when(promotionMapper.toResponse(promotion)).thenReturn(response);

        assertThat(service.create(eventId, request)).isSameAs(response);
        verify(promotion).setEvent(event);
    }

    // =====================================================================
    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void nominalPourcentage10_creeLaPromotionLieeALEvent() {
            creerAvecSucces(requestValide());
        }

        @Test
        void montantFixeSuperieurA100_estAutorise() {
            creerAvecSucces(request("FIXE150", TypeReduction.FIXED_AMOUNT, "150",
                    today.minusDays(1), today.plusDays(25), 10, orgaId));
        }

        @Test
        void pourcentageExactement100_estAutorise() {
            creerAvecSucces(request("GRATUIT", TypeReduction.PERCENTAGE, "100",
                    today.minusDays(1), today.plusDays(25), 10, orgaId));
        }

        @Test
        void dateFinEgaleAAujourdhui_estAutorisee() {
            creerAvecSucces(request("LASTDAY", TypeReduction.PERCENTAGE, "10",
                    today.minusDays(5), today, 10, orgaId));
        }

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(eventId, requestValide()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(eventId.toString());

            verify(promotionRepository, never()).save(any());
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            when(eventRepository.findById(eventId))
                    .thenReturn(Optional.of(event(StatusEvenment.PUBLISHED, UUID.randomUUID())));

            assertThatThrownBy(() -> service.create(eventId, requestValide()))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(promotionRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, names = {"CANCELLED", "COMPLETED"})
        void eventAnnuleOuTermine_leveBusinessRule(StatusEvenment statut) {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(statut, orgaId)));

            assertThatThrownBy(() -> service.create(eventId, requestValide()))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).save(any());
        }

        @Test
        void dateFinAvantDateDebut_leveBusinessRule() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(StatusEvenment.PUBLISHED, orgaId)));
            PromotionRequest request = request("X", TypeReduction.PERCENTAGE, "10",
                    today.plusDays(10), today.plusDays(5), 10, orgaId);

            assertThatThrownBy(() -> service.create(eventId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).existsByEventIdAndCode(any(), anyString());
            verify(promotionRepository, never()).save(any());
        }

        @Test
        void dateFinDejaPassee_leveBusinessRule() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(StatusEvenment.PUBLISHED, orgaId)));
            PromotionRequest request = request("X", TypeReduction.PERCENTAGE, "10",
                    today.minusDays(10), today.minusDays(1), 10, orgaId);

            assertThatThrownBy(() -> service.create(eventId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).save(any());
        }

        @Test
        void pourcentageSuperieurA100_leveBusinessRule() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(StatusEvenment.PUBLISHED, orgaId)));
            PromotionRequest request = request("X", TypeReduction.PERCENTAGE, "150",
                    today.minusDays(1), today.plusDays(5), 10, orgaId);

            assertThatThrownBy(() -> service.create(eventId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).save(any());
        }

        @Test
        void codeDejaExistantPourCetEvent_leveBusinessRule() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(StatusEvenment.PUBLISHED, orgaId)));
            when(promotionRepository.existsByEventIdAndCode(eventId, "TEST10")).thenReturn(true);

            assertThatThrownBy(() -> service.create(eventId, requestValide()))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listByEvent")
    class ListByEvent {

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listByEvent(eventId, orgaId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            when(eventRepository.findById(eventId))
                    .thenReturn(Optional.of(event(StatusEvenment.PUBLISHED, UUID.randomUUID())));

            assertThatThrownBy(() -> service.listByEvent(eventId, orgaId))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(promotionRepository, never()).findByEventId(any());
        }

        @Test
        void nominal_retourneLesPromotionsMappees() {
            Promotion p1 = mock(Promotion.class);
            Promotion p2 = mock(Promotion.class);
            PromotionResponse r1 = mock(PromotionResponse.class);
            PromotionResponse r2 = mock(PromotionResponse.class);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(StatusEvenment.DRAFT, orgaId)));
            when(promotionRepository.findByEventId(eventId)).thenReturn(List.of(p1, p2));
            when(promotionMapper.toResponse(p1)).thenReturn(r1);
            when(promotionMapper.toResponse(p2)).thenReturn(r2);

            assertThat(service.listByEvent(eventId, orgaId)).containsExactly(r1, r2);
        }

        @Test
        void aucunePromotion_retourneListeVide() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(StatusEvenment.DRAFT, orgaId)));
            when(promotionRepository.findByEventId(eventId)).thenReturn(List.of());

            assertThat(service.listByEvent(eventId, orgaId)).isEmpty();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void promotionIntrouvable_leveResourceNotFound() {
            when(promotionRepository.findById(promoId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(promoId, requestValide()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(promoId.toString());
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            Event event = event(StatusEvenment.PUBLISHED, UUID.randomUUID());
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion(event, "TEST10", 0)));

            assertThatThrownBy(() -> service.update(promoId, requestValide()))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(promotionMapper, never()).updateEntity(any(), any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, names = {"CANCELLED", "COMPLETED"})
        void eventAnnuleOuTermine_leveBusinessRule(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion(event, "TEST10", 0)));

            assertThatThrownBy(() -> service.update(promoId, requestValide()))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionMapper, never()).updateEntity(any(), any());
        }

        @Test
        void datesInvalides_leveBusinessRule() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion(event, "TEST10", 0)));
            PromotionRequest request = request("TEST10", TypeReduction.PERCENTAGE, "10",
                    today.plusDays(10), today.plusDays(5), 100, orgaId);

            assertThatThrownBy(() -> service.update(promoId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionMapper, never()).updateEntity(any(), any());
        }

        @Test
        void maxUtilisationsInferieurAuNombreDejaUtilise_leveBusinessRule() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion(event, "TEST10", 5)));
            PromotionRequest request = request("TEST10", TypeReduction.PERCENTAGE, "10",
                    today.minusDays(1), today.plusDays(5), 3, orgaId);

            assertThatThrownBy(() -> service.update(promoId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionMapper, never()).updateEntity(any(), any());
        }

        @Test
        void codeChangeMaisPromotionDejaUtilisee_leveBusinessRule() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion(event, "TEST10", 2)));
            PromotionRequest request = request("NOUVEAU", TypeReduction.PERCENTAGE, "10",
                    today.minusDays(1), today.plusDays(5), 100, orgaId);

            assertThatThrownBy(() -> service.update(promoId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).existsByEventIdAndCode(any(), anyString());
            verify(promotionMapper, never()).updateEntity(any(), any());
        }

        @Test
        void codeChangeMaisDejaPrisParUneAutrePromo_leveBusinessRule() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion(event, "TEST10", 0)));
            when(promotionRepository.existsByEventIdAndCode(eventId, "AUTRE")).thenReturn(true);
            PromotionRequest request = request("AUTRE", TypeReduction.PERCENTAGE, "10",
                    today.minusDays(1), today.plusDays(5), 100, orgaId);

            assertThatThrownBy(() -> service.update(promoId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionMapper, never()).updateEntity(any(), any());
        }

        @Test
        void codeChangeEtLibre_metAJourLaPromotion() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            Promotion promotion = promotion(event, "TEST10", 0);
            PromotionRequest request = request("NOUVEAU", TypeReduction.PERCENTAGE, "20",
                    today.minusDays(1), today.plusDays(5), 100, orgaId);
            PromotionResponse response = mock(PromotionResponse.class);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion));
            when(promotionRepository.existsByEventIdAndCode(eventId, "NOUVEAU")).thenReturn(false);
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            assertThat(service.update(promoId, request)).isSameAs(response);

            verify(promotionMapper).updateEntity(request, promotion);
        }

        @Test
        void memeCodeEtMaxEgalAuNombreUtilise_estAutorise_sansVerifierLUnicite() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            Promotion promotion = promotion(event, "TEST10", 3);   // deja utilisee 3 fois
            PromotionRequest request = request("TEST10", TypeReduction.PERCENTAGE, "15",
                    today.minusDays(1), today.plusDays(5), 3, orgaId);   // max = 3 (limite)
            PromotionResponse response = mock(PromotionResponse.class);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion));
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            assertThat(service.update(promoId, request)).isSameAs(response);

            verify(promotionRepository, never()).existsByEventIdAndCode(any(), anyString());
            verify(promotionMapper).updateEntity(request, promotion);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void promotionIntrouvable_leveResourceNotFound() {
            when(promotionRepository.findById(promoId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(promoId, orgaId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository, never()).delete(any());
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            Event event = event(StatusEvenment.PUBLISHED, UUID.randomUUID());
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion(event, "TEST10", 0)));

            assertThatThrownBy(() -> service.delete(promoId, orgaId))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(promotionRepository, never()).delete(any());
        }

        @Test
        void promotionDejaUtilisee_leveBusinessRule() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion(event, "TEST10", 1)));

            assertThatThrownBy(() -> service.delete(promoId, orgaId))
                    .isInstanceOf(BusinessRuleException.class);

            verify(promotionRepository, never()).delete(any());
        }

        @Test
        void nominal_supprimeLaPromotionNonUtilisee() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            Promotion promotion = promotion(event, "TEST10", 0);
            when(promotionRepository.findById(promoId)).thenReturn(Optional.of(promotion));

            service.delete(promoId, orgaId);

            verify(promotionRepository).delete(promotion);
        }
    }
}
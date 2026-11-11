package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.categorie.CategorieBilletRequest;
import com.eventPro.eventPro.dto.categorie.CategorieBilletResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.categorie.CategorieBilletMapper;
import com.eventPro.eventPro.model.CategorieBillet;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.EventRepository;
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
import java.util.List;
import java.util.Map;
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
class CategorieBilletServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private CategorieBilletMapper categorieBilletMapper;
    @Mock private CategorieBilletRepository categorieBilletRepository;
    @Mock private DisponibiliteService disponibiliteService;

    @InjectMocks private CategorieBilletSercice service;   // (renomme en CategorieBilletService si tu corriges la faute)

    private final UUID eventId = UUID.randomUUID();
    private final UUID orgaId = UUID.randomUUID();
    private final UUID categorieId = UUID.randomUUID();

    // ================= helpers =================

    private CategorieBilletRequest request(String nom, int quantite, int quota, UUID owner) {
        CategorieBilletRequest r = mock(CategorieBilletRequest.class);
        lenient().when(r.nom()).thenReturn(nom);
        lenient().when(r.quantiteTotale()).thenReturn(quantite);
        lenient().when(r.quotaMaxParClient()).thenReturn(quota);
        lenient().when(r.organisateurId()).thenReturn(owner);
        return r;
    }

    private CategorieBilletRequest requestValide() {
        return request("VIP", 10, 4, orgaId);
    }

    private Event event(StatusEvenment statut, UUID ownerId) {
        Organisateur orga = Organisateur.builder().id(ownerId).build();
        return Event.builder().id(eventId).statut(statut).organisateur(orga).build();
    }

    private CategorieBillet categorie(Event event, String nom) {
        return CategorieBillet.builder().id(categorieId).nom(nom).event(event)
                .prixUnitaire(new BigDecimal("100.00")).quantiteTotale(10L).quotaMaxParClient(4).build();
    }
    // =====================================================================
    @Nested
    @DisplayName("create")
    class Create {

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, names = {"DRAFT", "PUBLISHED"})
        void nominal_lieLaCategorieALEvent_etPlacesEgalesALaQuantite(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            CategorieBilletRequest request = requestValide();
            CategorieBillet categorie = categorie(event, "VIP");
            CategorieBillet saved = categorie(event, "VIP");
            CategorieBilletResponse response = mock(CategorieBilletResponse.class);

            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId, "VIP")).thenReturn(false);
            when(categorieBilletMapper.toEntity(request)).thenReturn(categorie);
            when(categorieBilletRepository.save(categorie)).thenReturn(saved);
            when(categorieBilletMapper.toResponse(saved, 10)).thenReturn(response);

            CategorieBilletResponse result = service.create(eventId, request);

            assertThat(result).isSameAs(response);
            assertThat(categorie.getEvent()).isSameAs(event);
        }

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(eventId, requestValide()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(eventId.toString());

            verify(categorieBilletRepository, never()).save(any());
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            Event event = event(StatusEvenment.DRAFT, UUID.randomUUID());
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> service.create(eventId, requestValide()))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(categorieBilletRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, names = {"CANCELLED", "COMPLETED"})
        void eventAnnuleOuTermine_leveBusinessRule(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> service.create(eventId, requestValide()))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletRepository, never()).save(any());
        }

        @Test
        void quotaSuperieurALaQuantite_leveBusinessRule_sansVerifierLeNom() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> service.create(eventId, request("VIP", 5, 10, orgaId)))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletRepository, never()).existsByEventIdAndNomIgnoreCase(any(), anyString());
            verify(categorieBilletRepository, never()).save(any());
        }

        @Test
        void nomDejaUtilisePourCetEvent_leveBusinessRule() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId, "VIP")).thenReturn(true);

            assertThatThrownBy(() -> service.create(eventId, requestValide()))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletRepository, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listByEvent")
    class ListByEvent {

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listByEvent(eventId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, mode = EnumSource.Mode.EXCLUDE, names = "PUBLISHED")
        void eventNonPublie_estInvisible_404(StatusEvenment statut) {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(statut, orgaId)));

            assertThatThrownBy(() -> service.listByEvent(eventId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(categorieBilletRepository, never()).findByEventId(any());
        }

        @Test
        void nominal_retourneChaqueCategorieAvecSesPlaces() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            CategorieBillet c1 = mock(CategorieBillet.class);
            CategorieBillet c2 = mock(CategorieBillet.class);
            when(c1.getId()).thenReturn(id1);
            when(c2.getId()).thenReturn(id2);
            List<CategorieBillet> categories = List.of(c1, c2);
            CategorieBilletResponse r1 = mock(CategorieBilletResponse.class);
            CategorieBilletResponse r2 = mock(CategorieBilletResponse.class);

            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(StatusEvenment.PUBLISHED, orgaId)));
            when(categorieBilletRepository.findByEventId(eventId)).thenReturn(categories);
            when(disponibiliteService.placesParCategorie(eventId, categories)).thenReturn(Map.of(id1, 8, id2, 0));
            when(categorieBilletMapper.toResponse(c1, 8)).thenReturn(r1);
            when(categorieBilletMapper.toResponse(c2, 0)).thenReturn(r2);

            assertThat(service.listByEvent(eventId)).containsExactly(r1, r2);
        }

        @Test
        void aucuneCategorie_retourneListeVide() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(StatusEvenment.PUBLISHED, orgaId)));
            when(categorieBilletRepository.findByEventId(eventId)).thenReturn(List.of());
            when(disponibiliteService.placesParCategorie(eventId, List.of())).thenReturn(Map.of());

            assertThat(service.listByEvent(eventId)).isEmpty();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void categorieIntrouvable_leveResourceNotFound() {
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(categorieId, requestValide()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(categorieId.toString());
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            Event event = event(StatusEvenment.DRAFT, UUID.randomUUID());
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie(event, "VIP")));

            assertThatThrownBy(() -> service.update(categorieId, requestValide()))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(categorieBilletMapper, never()).updateEntity(any(), any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, names = {"CANCELLED", "COMPLETED"})
        void eventAnnuleOuTermine_leveBusinessRule(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie(event, "VIP")));

            assertThatThrownBy(() -> service.update(categorieId, requestValide()))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletMapper, never()).updateEntity(any(), any());
        }

        @Test
        void quotaSuperieurALaQuantite_leveBusinessRule() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie(event, "VIP")));

            assertThatThrownBy(() -> service.update(categorieId, request("VIP", 5, 10, orgaId)))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletMapper, never()).updateEntity(any(), any());
        }

        @Test
        void nomChangeEtDejaPris_leveBusinessRule() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie(event, "VIP")));
            when(categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId, "Standard")).thenReturn(true);

            assertThatThrownBy(() -> service.update(categorieId, request("Standard", 10, 4, orgaId)))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletMapper, never()).updateEntity(any(), any());
        }

        @Test
        void nomChangeEtLibre_metAJourEtRetourneLesPlaces() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            CategorieBillet categorie = categorie(event, "VIP");
            CategorieBilletRequest request = request("Premium", 10, 4, orgaId);
            CategorieBilletResponse response = mock(CategorieBilletResponse.class);

            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie));
            when(categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId, "Premium")).thenReturn(false);
            when(disponibiliteService.placesParCategorie(eventId, List.of(categorie))).thenReturn(Map.of(categorieId, 3));
            when(categorieBilletMapper.toResponse(categorie, 3)).thenReturn(response);

            CategorieBilletResponse result = service.update(categorieId, request);

            assertThat(result).isSameAs(response);
            verify(categorieBilletMapper).updateEntity(request, categorie);
        }

        @Test
        void memeNomAvecCasseDifferente_neVerifiePasLUnicite() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            CategorieBillet categorie = categorie(event, "VIP");
            CategorieBilletRequest request = request("vip", 10, 4, orgaId);
            CategorieBilletResponse response = mock(CategorieBilletResponse.class);

            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie));
            when(disponibiliteService.placesParCategorie(eventId, List.of(categorie))).thenReturn(Map.of(categorieId, 3));
            when(categorieBilletMapper.toResponse(categorie, 3)).thenReturn(response);

            assertThat(service.update(categorieId, request)).isSameAs(response);

            verify(categorieBilletRepository, never()).existsByEventIdAndNomIgnoreCase(any(), anyString());
            verify(categorieBilletMapper).updateEntity(request, categorie);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void categorieIntrouvable_leveResourceNotFound() {
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(categorieId, orgaId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(categorieBilletRepository, never()).delete(any());
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            Event event = event(StatusEvenment.DRAFT, UUID.randomUUID());
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie(event, "VIP")));

            assertThatThrownBy(() -> service.delete(categorieId, orgaId))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(categorieBilletRepository, never()).delete(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, names = {"CANCELLED", "COMPLETED"})
        void eventAnnuleOuTermine_leveBusinessRule(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie(event, "VIP")));

            assertThatThrownBy(() -> service.delete(categorieId, orgaId))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletRepository, never()).delete(any());
        }

        @Test
        void derniereCategorieDUnEventPublie_leveBusinessRule() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie(event, "VIP")));
            when(categorieBilletRepository.countByEventId(eventId)).thenReturn(1L);

            assertThatThrownBy(() -> service.delete(categorieId, orgaId))
                    .isInstanceOf(BusinessRuleException.class);

            verify(categorieBilletRepository, never()).delete(any());
        }

        @Test
        void eventPublieAvecPlusieursCategories_supprime() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            CategorieBillet categorie = categorie(event, "VIP");
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie));
            when(categorieBilletRepository.countByEventId(eventId)).thenReturn(2L);

            service.delete(categorieId, orgaId);

            verify(categorieBilletRepository).delete(categorie);
        }

        @Test
        void eventDraftAvecUneSeuleCategorie_supprimeSansCompter() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            CategorieBillet categorie = categorie(event, "VIP");
            when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie));

            service.delete(categorieId, orgaId);

            verify(categorieBilletRepository).delete(categorie);
            verify(categorieBilletRepository, never()).countByEventId(any());
        }
    }
}
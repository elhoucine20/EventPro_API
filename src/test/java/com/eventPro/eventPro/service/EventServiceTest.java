package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.categorie.CategorieBilletCreation;
import com.eventPro.eventPro.dto.event.EventRequest;
import com.eventPro.eventPro.dto.event.EventResponse;
import com.eventPro.eventPro.exception.BusinessRuleException;
import com.eventPro.eventPro.exception.ForbiddenOperationException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.event.EventMapper;
import com.eventPro.eventPro.model.CategorieBillet;
import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.EventRepository;
import com.eventPro.eventPro.repository.OrganisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private OrganisateurRepository organisateurRepository;
    @Mock private EventMapper eventMapper;
    @Mock private CategorieBilletRepository categorieBilletRepository;
    @Mock private DisponibiliteService disponibiliteService;

    @InjectMocks private EventService eventService;

    @Captor private ArgumentCaptor<List<CategorieBillet>> categoriesCaptor;

    private final UUID eventId = UUID.randomUUID();
    private final UUID orgaId = UUID.randomUUID();

    private final LocalDateTime now = LocalDateTime.now();
    private final LocalDateTime debut = now.plusDays(30);
    private final LocalDateTime fin = debut.plusHours(3);
    private final LocalDateTime ouverture = now.minusDays(1);
    private final LocalDateTime cloture = now.plusDays(29);

    // ================= helpers =================

    /** Requete avec les dates voulues (stubs lenient : la validation peut s'arreter avant de les lire toutes). */
    private EventRequest request(LocalDateTime d, LocalDateTime f, LocalDateTime o, LocalDateTime c,
                                 List<CategorieBilletCreation> categories) {
        EventRequest r = mock(EventRequest.class);
        lenient().when(r.dateDebut()).thenReturn(d);
        lenient().when(r.dateFin()).thenReturn(f);
        lenient().when(r.dateOuvertureVente()).thenReturn(o);
        lenient().when(r.dateClotureVente()).thenReturn(c);
        lenient().when(r.organisateurId()).thenReturn(orgaId);
        lenient().when(r.categories()).thenReturn(categories);
        return r;
    }

    private EventRequest requestValide(List<CategorieBilletCreation> categories) {
        return request(debut, fin, ouverture, cloture, categories);
    }

    private CategorieBilletCreation cat(String nom, int quantite, int quota) {
        CategorieBilletCreation c = mock(CategorieBilletCreation.class);
        lenient().when(c.nom()).thenReturn(nom);
        lenient().when(c.prixUnitaire()).thenReturn(new BigDecimal("100.00"));
        lenient().when(c.quantiteTotale()).thenReturn(quantite);
        lenient().when(c.quotaMaxParClient()).thenReturn(quota);
        return c;
    }

    private Event event(StatusEvenment statut, UUID ownerId) {
        Event e = mock(Event.class);
        Organisateur o = mock(Organisateur.class);
        lenient().when(o.getId()).thenReturn(ownerId);
        lenient().when(e.getId()).thenReturn(eventId);
        lenient().when(e.getOrganisateur()).thenReturn(o);
        lenient().when(e.getStatut()).thenReturn(statut);
        return e;
    }

    /** toResponse(event) : places = 7 (stub disponibilite + mapper). */
    private EventResponse stubResponse(Event event) {
        EventResponse response = mock(EventResponse.class);
        when(disponibiliteService.placesParEvent(List.of(eventId))).thenReturn(Map.of(eventId, 7));
        when(eventMapper.toResponse(event, 7)).thenReturn(response);
        return response;
    }

    // =====================================================================
    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void nominalSansCategories_creeEnDraft() {
            EventRequest request = requestValide(null);
            Organisateur orga = mock(Organisateur.class);
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
            when(eventMapper.toEntity(request)).thenReturn(event);
            when(eventRepository.save(event)).thenReturn(event);
            EventResponse response = stubResponse(event);

            EventResponse result = eventService.create(request);

            assertThat(result).isSameAs(response);
            verify(event).setStatut(StatusEvenment.DRAFT);
            verify(event).setOrganisateur(orga);
            verify(categorieBilletRepository, never()).saveAll(any());
        }

        @Test
        void nominalAvecCategories_sauvegardeLesCategoriesLieesALEvent() {
            List<CategorieBilletCreation> cats = List.of(cat("  VIP ", 10, 4), cat("Standard", 100, 6));
            EventRequest request = requestValide(cats);
            Organisateur orga = mock(Organisateur.class);
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
            when(eventMapper.toEntity(request)).thenReturn(event);
            when(eventRepository.save(event)).thenReturn(event);
            stubResponse(event);

            eventService.create(request);

            verify(categorieBilletRepository).saveAll(categoriesCaptor.capture());
            List<CategorieBillet> saved = categoriesCaptor.getValue();
            assertThat(saved).hasSize(2);
            assertThat(saved).extracting(CategorieBillet::getNom).containsExactly("VIP", "Standard");
            assertThat(saved).extracting(CategorieBillet::getEvent).containsOnly(event);
        }

        @Test
        void listeDeCategoriesVide_neSauvegardeAucuneCategorie() {
            EventRequest request = requestValide(List.of());
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(mock(Organisateur.class)));
            when(eventMapper.toEntity(request)).thenReturn(event);
            when(eventRepository.save(event)).thenReturn(event);
            stubResponse(event);

            eventService.create(request);

            verify(categorieBilletRepository, never()).saveAll(any());
        }

        @Test
        void dateDebutDansLePasse_leveBusinessRule() {
            EventRequest request = request(now.minusDays(1), fin, ouverture, cloture, null);

            assertThatThrownBy(() -> eventService.create(request))
                    .isInstanceOf(BusinessRuleException.class);

            verifyNoInteractions(organisateurRepository, eventMapper);
            verify(eventRepository, never()).save(any());
        }

        @Test
        void dateFinAvantDateDebut_leveBusinessRule() {
            EventRequest request = request(debut, debut.minusHours(1), ouverture, cloture, null);

            assertThatThrownBy(() -> eventService.create(request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(eventRepository, never()).save(any());
        }

        @Test
        void ouvertureVenteApresOuEgaleACloture_leveBusinessRule() {
            EventRequest request = request(debut, fin, cloture, cloture, null);

            assertThatThrownBy(() -> eventService.create(request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(eventRepository, never()).save(any());
        }

        @Test
        void clotureVenteApresLeDebut_leveBusinessRule() {
            EventRequest request = request(debut, fin, ouverture, debut.plusHours(1), null);

            assertThatThrownBy(() -> eventService.create(request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(eventRepository, never()).save(any());
        }

        @Test
        void quotaSuperieurALaQuantite_leveBusinessRule_avantToutAcces() {
            EventRequest request = requestValide(List.of(cat("VIP", 5, 10)));

            assertThatThrownBy(() -> eventService.create(request))
                    .isInstanceOf(BusinessRuleException.class);

            verifyNoInteractions(organisateurRepository, eventMapper);
            verify(eventRepository, never()).save(any());
        }

        @Test
        void nomsDeCategoriesEnDouble_ignoreCasseEtEspaces_leveBusinessRule() {
            EventRequest request = requestValide(List.of(cat("VIP", 10, 4), cat("  vip ", 20, 4)));

            assertThatThrownBy(() -> eventService.create(request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(eventRepository, never()).save(any());
            verify(categorieBilletRepository, never()).saveAll(any());
        }

        @Test
        void organisateurIntrouvable_leveResourceNotFound() {
            EventRequest request = requestValide(null);
            when(organisateurRepository.findById(orgaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(orgaId.toString());

            verify(eventRepository, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.update(eventId, requestValide(null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            Event event = event(StatusEvenment.DRAFT, UUID.randomUUID());
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.update(eventId, requestValide(null)))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(eventMapper, never()).updateEntity(any(), any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
        void statutDifferentDeDraft_leveBusinessRule(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.update(eventId, requestValide(null)))
                    .isInstanceOf(BusinessRuleException.class);

            verify(eventMapper, never()).updateEntity(any(), any());
        }

        @Test
        void datesInvalides_leveBusinessRule_etNeModifieRien() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            EventRequest request = request(debut, debut.minusHours(2), ouverture, cloture, null);

            assertThatThrownBy(() -> eventService.update(eventId, request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(eventMapper, never()).updateEntity(any(), any());
        }

        @Test
        void nominal_metAJourLEntite() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            EventRequest request = requestValide(null);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            EventResponse response = stubResponse(event);

            EventResponse result = eventService.update(eventId, request);

            assertThat(result).isSameAs(response);
            verify(eventMapper).updateEntity(request, event);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("publier")
    class Publier {

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.publier(eventId, orgaId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            Event event = event(StatusEvenment.DRAFT, UUID.randomUUID());
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.publier(eventId, orgaId))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(event, never()).setStatut(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
        void statutDifferentDeDraft_leveBusinessRule(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.publier(eventId, orgaId))
                    .isInstanceOf(BusinessRuleException.class);

            verify(event, never()).setStatut(any());
        }

        @Test
        void sansAucuneCategorie_leveBusinessRule() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(categorieBilletRepository.existsByEventId(eventId)).thenReturn(false);

            assertThatThrownBy(() -> eventService.publier(eventId, orgaId))
                    .isInstanceOf(BusinessRuleException.class);

            verify(event, never()).setStatut(any());
        }

        @Test
        void nominal_passeEnPublished() {
            Event event = event(StatusEvenment.DRAFT, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(categorieBilletRepository.existsByEventId(eventId)).thenReturn(true);
            EventResponse response = stubResponse(event);

            EventResponse result = eventService.publier(eventId, orgaId);

            assertThat(result).isSameAs(response);
            verify(event).setStatut(StatusEvenment.PUBLISHED);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("annuler")
    class Annuler {

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.annuler(eventId, orgaId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void pasLOrganisateur_leveForbidden() {
            Event event = event(StatusEvenment.PUBLISHED, UUID.randomUUID());
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.annuler(eventId, orgaId))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(event, never()).setStatut(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, names = {"CANCELLED", "COMPLETED"})
        void dejaAnnuleOuTermine_leveBusinessRule(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.annuler(eventId, orgaId))
                    .isInstanceOf(BusinessRuleException.class);

            verify(event, never()).setStatut(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, names = {"DRAFT", "PUBLISHED"})
        void nominal_passeEnCancelled(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            EventResponse response = stubResponse(event);

            EventResponse result = eventService.annuler(eventId, orgaId);

            assertThat(result).isSameAs(response);
            verify(event).setStatut(StatusEvenment.CANCELLED);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void eventIntrouvable_leveResourceNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getById(eventId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @ParameterizedTest
        @EnumSource(value = StatusEvenment.class, mode = EnumSource.Mode.EXCLUDE, names = "PUBLISHED")
        void eventNonPublie_estInvisible_404(StatusEvenment statut) {
            Event event = event(statut, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.getById(eventId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(eventMapper, disponibiliteService);
        }

        @Test
        void eventPublie_retourneLaReponse() {
            Event event = event(StatusEvenment.PUBLISHED, orgaId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            EventResponse response = stubResponse(event);

            assertThat(eventService.getById(eventId)).isSameAs(response);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listes")
    class Listes {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        void listPublished_mappeAvecLesPlaces_etZeroParDefaut() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Event e1 = mock(Event.class);
            Event e2 = mock(Event.class);
            when(e1.getId()).thenReturn(id1);
            when(e2.getId()).thenReturn(id2);
            Page<Event> page = new PageImpl<>(List.of(e1, e2), pageable, 2);
            EventResponse r1 = mock(EventResponse.class);
            EventResponse r2 = mock(EventResponse.class);

            when(eventRepository.findByStatut(StatusEvenment.PUBLISHED, pageable)).thenReturn(page);
            when(disponibiliteService.placesParEvent(anyList())).thenReturn(Map.of(id1, 4)); // id2 absent
            when(eventMapper.toResponse(e1, 4)).thenReturn(r1);
            when(eventMapper.toResponse(e2, 0)).thenReturn(r2);

            Page<EventResponse> result = eventService.listPublished(pageable);

            assertThat(result.getContent()).containsExactly(r1, r2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void listPublished_pageVide() {
            when(eventRepository.findByStatut(StatusEvenment.PUBLISHED, pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));
            when(disponibiliteService.placesParEvent(anyList())).thenReturn(Map.of());

            assertThat(eventService.listPublished(pageable).getContent()).isEmpty();
        }

        @Test
        void listByOrganisateur_organisateurIntrouvable_leveResourceNotFound() {
            when(organisateurRepository.existsById(orgaId)).thenReturn(false);

            assertThatThrownBy(() -> eventService.listByOrganisateur(orgaId, pageable))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(orgaId.toString());

            verify(eventRepository, never()).findByOrganisateurId(any(), any());
        }

        @Test
        void listByOrganisateur_nominal_retourneSesEvents() {
            Event e1 = mock(Event.class);
            when(e1.getId()).thenReturn(eventId);
            EventResponse r1 = mock(EventResponse.class);
            when(organisateurRepository.existsById(orgaId)).thenReturn(true);
            when(eventRepository.findByOrganisateurId(orgaId, pageable))
                    .thenReturn(new PageImpl<>(List.of(e1), pageable, 1));
            when(disponibiliteService.placesParEvent(anyList())).thenReturn(Map.of(eventId, 3));
            when(eventMapper.toResponse(e1, 3)).thenReturn(r1);

            assertThat(eventService.listByOrganisateur(orgaId, pageable).getContent()).containsExactly(r1);
        }
    }
}
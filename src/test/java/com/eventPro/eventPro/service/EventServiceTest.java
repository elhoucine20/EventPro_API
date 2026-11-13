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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT) // pas d'erreur si un stub n'est pas utilise dans un test
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganisateurRepository organisateurRepository;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private CategorieBilletRepository categorieBilletRepository;
    @Mock
    private DisponibiliteService disponibiliteService;

    @InjectMocks
    private EventService eventService; // le vrai service avec les faux repository dedans

    @Captor
    private ArgumentCaptor<List<CategorieBillet>> categoriesCaptor; // pour recuperer la liste envoyee a saveAll

    private final UUID eventId = UUID.randomUUID();
    private final UUID orgaId = UUID.randomUUID();

    // dates valides par defaut
    private final LocalDateTime now = LocalDateTime.now();
    private final LocalDateTime debut = now.plusDays(30);
    private final LocalDateTime fin = debut.plusHours(3);
    private final LocalDateTime ouverture = now.minusDays(1);
    private final LocalDateTime cloture = now.plusDays(29);

    // ---------- petits helpers (comme OneUser() avant) ----------
    private Organisateur OneOrganisateur(UUID id) {
        return Organisateur.builder().id(id).build();
    }

    private Event OneEvent(StatusEvenment statut, UUID ownerId) {
        return Event.builder().id(eventId).statut(statut).organisateur(OneOrganisateur(ownerId)).build();
    }

    // requete avec les dates voulues
    private EventRequest OneRequest(LocalDateTime d, LocalDateTime f, LocalDateTime o, LocalDateTime c,
                                    List<CategorieBilletCreation> categories) {
        EventRequest r = mock(EventRequest.class);
        when(r.dateDebut()).thenReturn(d);
        when(r.dateFin()).thenReturn(f);
        when(r.dateOuvertureVente()).thenReturn(o);
        when(r.dateClotureVente()).thenReturn(c);
        when(r.organisateurId()).thenReturn(orgaId);
        when(r.categories()).thenReturn(categories);
        return r;
    }

    private EventRequest RequestValide(List<CategorieBilletCreation> categories) {
        return OneRequest(debut, fin, ouverture, cloture, categories);
    }

    private CategorieBilletCreation OneCategorie(String nom, int quantite, int quota) {
        CategorieBilletCreation c = mock(CategorieBilletCreation.class);
        when(c.nom()).thenReturn(nom);
        when(c.prixUnitaire()).thenReturn(new BigDecimal("100.00"));
        when(c.quantiteTotale()).thenReturn(quantite);
        when(c.quotaMaxParClient()).thenReturn(quota);
        return c;
    }

    // prepare la reponse de toResponse(event) : 7 places disponibles
    private EventResponse stubResponse(Event event) {
        EventResponse response = mock(EventResponse.class);
        when(disponibiliteService.placesParEvent(List.of(event.getId()))).thenReturn(Map.of(event.getId(), 7));
        when(eventMapper.toResponse(event, 7)).thenReturn(response);
        return response;
    }

    // =====================================================================
    // create
    // =====================================================================
    @Test
    void create_sansCategories_creeEnDraft() {
        // ARRANGE
        EventRequest request = RequestValide(null);
        Organisateur orga = OneOrganisateur(orgaId);
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId); // le service doit le remettre en DRAFT
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(orga));
        when(eventMapper.toEntity(request)).thenReturn(event);
        when(eventRepository.save(event)).thenReturn(event);
        EventResponse response = stubResponse(event);

        // ACT
        EventResponse result = eventService.create(request);

        // ASSERT
        assertSame(response, result);
        assertEquals(StatusEvenment.DRAFT, event.getStatut()); // toujours DRAFT a la creation
        assertSame(orga, event.getOrganisateur());              // lie a l'organisateur
        verify(categorieBilletRepository, never()).saveAll(any()); // pas de categories
    }

    @Test
    void create_avecCategories_sauvegardeLesCategoriesLieesALEvent() {
        // ARRANGE
        List<CategorieBilletCreation> cats = List.of(OneCategorie("  VIP ", 10, 4), OneCategorie("Standard", 100, 6));
        EventRequest request = RequestValide(cats);
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(OneOrganisateur(orgaId)));
        when(eventMapper.toEntity(request)).thenReturn(event);
        when(eventRepository.save(event)).thenReturn(event);
        stubResponse(event);

        // ACT
        eventService.create(request);

        // ASSERT : on recupere la liste envoyee a saveAll
        verify(categorieBilletRepository).saveAll(categoriesCaptor.capture());
        List<CategorieBillet> saved = categoriesCaptor.getValue();
        assertEquals(2, saved.size());
        assertEquals("VIP", saved.get(0).getNom());      // espaces enleves (strip)
        assertEquals("Standard", saved.get(1).getNom());
        assertSame(event, saved.get(0).getEvent());      // chaque categorie est liee a l'event
        assertSame(event, saved.get(1).getEvent());
    }

    @Test
    void create_listeDeCategoriesVide_neSauvegardeAucuneCategorie() {
        // ARRANGE
        EventRequest request = RequestValide(List.of());
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.of(OneOrganisateur(orgaId)));
        when(eventMapper.toEntity(request)).thenReturn(event);
        when(eventRepository.save(event)).thenReturn(event);
        stubResponse(event);

        // ACT
        eventService.create(request);

        // ASSERT
        verify(categorieBilletRepository, never()).saveAll(any());
    }

    @Test
    void create_dateDebutDansLePasse_BusinessRule() {
        // ARRANGE : debut hier
        EventRequest request = OneRequest(now.minusDays(1), fin, ouverture, cloture, null);

        // ACT + ASSERT
        assertThrows(BusinessRuleException.class, () -> eventService.create(request));

        verifyNoInteractions(organisateurRepository, eventMapper); // la validation est avant tout acces
        verify(eventRepository, never()).save(any());
    }

    @Test
    void create_dateFinAvantDateDebut_BusinessRule() {
        EventRequest request = OneRequest(debut, debut.minusHours(1), ouverture, cloture, null);

        assertThrows(BusinessRuleException.class, () -> eventService.create(request));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void create_ouvertureVenteApresOuEgaleACloture_BusinessRule() {
        EventRequest request = OneRequest(debut, fin, cloture, cloture, null);

        assertThrows(BusinessRuleException.class, () -> eventService.create(request));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void create_clotureVenteApresLeDebut_BusinessRule() {
        EventRequest request = OneRequest(debut, fin, ouverture, debut.plusHours(1), null);

        assertThrows(BusinessRuleException.class, () -> eventService.create(request));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void create_quotaSuperieurALaQuantite_BusinessRule() {
        // quota 10 > quantite 5
        EventRequest request = RequestValide(List.of(OneCategorie("VIP", 5, 10)));

        assertThrows(BusinessRuleException.class, () -> eventService.create(request));

        verifyNoInteractions(organisateurRepository, eventMapper);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void create_nomsDeCategoriesEnDouble_ignoreCasseEtEspaces_BusinessRule() {
        EventRequest request = RequestValide(List.of(OneCategorie("VIP", 10, 4), OneCategorie("  vip ", 20, 4)));

        assertThrows(BusinessRuleException.class, () -> eventService.create(request));

        verify(eventRepository, never()).save(any());
        verify(categorieBilletRepository, never()).saveAll(any());
    }

    @Test
    void create_organisateurIntrouvable_ResourceNotFound() {
        EventRequest request = RequestValide(null);
        when(organisateurRepository.findById(orgaId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eventService.create(request));

        assertTrue(ex.getMessage().contains(orgaId.toString()));
        verify(eventRepository, never()).save(any());
    }

    // =====================================================================
    // update
    // =====================================================================
    @Test
    void update_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.update(eventId, RequestValide(null)));
    }

    @Test
    void update_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.DRAFT, UUID.randomUUID()); // un autre organisateur
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ForbiddenOperationException.class, () -> eventService.update(eventId, RequestValide(null)));

        verify(eventMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_eventPublished_BusinessRule() {
        updateRefuse(StatusEvenment.PUBLISHED);
    }

    @Test
    void update_eventCancelled_BusinessRule() {
        updateRefuse(StatusEvenment.CANCELLED);
    }

    @Test
    void update_eventCompleted_BusinessRule() {
        updateRefuse(StatusEvenment.COMPLETED);
    }

    @Test
    void update_datesInvalides_BusinessRule_etNeModifieRien() {
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        EventRequest request = OneRequest(debut, debut.minusHours(2), ouverture, cloture, null);

        assertThrows(BusinessRuleException.class, () -> eventService.update(eventId, request));

        verify(eventMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_nominal_metAJourLEntite() {
        // ARRANGE
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        EventRequest request = RequestValide(null);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        EventResponse response = stubResponse(event);

        // ACT
        EventResponse result = eventService.update(eventId, request);

        // ASSERT
        assertSame(response, result);
        verify(eventMapper).updateEntity(request, event); // l'entite est mise a jour par le mapper
    }

    // =====================================================================
    // publier
    // =====================================================================
    @Test
    void publier_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.publier(eventId, orgaId));
    }

    @Test
    void publier_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.DRAFT, UUID.randomUUID());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ForbiddenOperationException.class, () -> eventService.publier(eventId, orgaId));

        assertEquals(StatusEvenment.DRAFT, event.getStatut()); // pas change
    }

    @Test
    void publier_eventDejaPublished_BusinessRule() {
        publierRefuse(StatusEvenment.PUBLISHED);
    }

    @Test
    void publier_eventCancelled_BusinessRule() {
        publierRefuse(StatusEvenment.CANCELLED);
    }

    @Test
    void publier_eventCompleted_BusinessRule() {
        publierRefuse(StatusEvenment.COMPLETED);
    }

    @Test
    void publier_sansAucuneCategorie_BusinessRule() {
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(categorieBilletRepository.existsByEventId(eventId)).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> eventService.publier(eventId, orgaId));

        assertEquals(StatusEvenment.DRAFT, event.getStatut()); // reste DRAFT
    }

    @Test
    void publier_nominal_passeEnPublished() {
        // ARRANGE
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(categorieBilletRepository.existsByEventId(eventId)).thenReturn(true);
        EventResponse response = stubResponse(event);

        // ACT
        EventResponse result = eventService.publier(eventId, orgaId);

        // ASSERT
        assertSame(response, result);
        assertEquals(StatusEvenment.PUBLISHED, event.getStatut());
    }

    // =====================================================================
    // annuler
    // =====================================================================
    @Test
    void annuler_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.annuler(eventId, orgaId));
    }

    @Test
    void annuler_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, UUID.randomUUID());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ForbiddenOperationException.class, () -> eventService.annuler(eventId, orgaId));

        assertEquals(StatusEvenment.PUBLISHED, event.getStatut());
    }

    @Test
    void annuler_dejaCancelled_BusinessRule() {
        Event event = OneEvent(StatusEvenment.CANCELLED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BusinessRuleException.class, () -> eventService.annuler(eventId, orgaId));
    }

    @Test
    void annuler_dejaCompleted_BusinessRule() {
        Event event = OneEvent(StatusEvenment.COMPLETED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BusinessRuleException.class, () -> eventService.annuler(eventId, orgaId));

        assertEquals(StatusEvenment.COMPLETED, event.getStatut());
    }

    @Test
    void annuler_depuisPublished_passeEnCancelled() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        EventResponse response = stubResponse(event);

        EventResponse result = eventService.annuler(eventId, orgaId);

        assertSame(response, result);
        assertEquals(StatusEvenment.CANCELLED, event.getStatut());
    }

    @Test
    void annuler_depuisDraft_passeEnCancelled() {
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        stubResponse(event);

        eventService.annuler(eventId, orgaId);

        assertEquals(StatusEvenment.CANCELLED, event.getStatut());
    }

    // =====================================================================
    // getById
    // =====================================================================
    @Test
    void getById_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.getById(eventId));
    }

    @Test
    void getById_eventDraft_estInvisible_404() {
        getByIdInvisible(StatusEvenment.DRAFT);
    }

    @Test
    void getById_eventCancelled_estInvisible_404() {
        getByIdInvisible(StatusEvenment.CANCELLED);
    }

    @Test
    void getById_eventCompleted_estInvisible_404() {
        getByIdInvisible(StatusEvenment.COMPLETED);
    }

    @Test
    void getById_eventPublie_retourneLaReponse() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        EventResponse response = stubResponse(event);

        assertSame(response, eventService.getById(eventId));
    }

    // =====================================================================
    // listPublished / listByOrganisateur
    // =====================================================================
    @Test
    void listPublished_mappeAvecLesPlaces_etZeroParDefaut() {
        // ARRANGE : e1 a des places connues (4), e2 n'est pas dans la map => 0
        Pageable pageable = PageRequest.of(0, 10);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Event e1 = Event.builder().id(id1).build();
        Event e2 = Event.builder().id(id2).build();
        EventResponse r1 = mock(EventResponse.class);
        EventResponse r2 = mock(EventResponse.class);
        when(eventRepository.findByStatut(StatusEvenment.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(e1, e2), pageable, 2));
        when(disponibiliteService.placesParEvent(anyList())).thenReturn(Map.of(id1, 4));
        when(eventMapper.toResponse(e1, 4)).thenReturn(r1);
        when(eventMapper.toResponse(e2, 0)).thenReturn(r2);

        // ACT
        Page<EventResponse> result = eventService.listPublished(pageable);

        // ASSERT
        assertEquals(2, result.getContent().size());
        assertSame(r1, result.getContent().get(0));
        assertSame(r2, result.getContent().get(1));
    }

    @Test
    void listPublished_pageVide() {
        Pageable pageable = PageRequest.of(0, 10);
        when(eventRepository.findByStatut(StatusEvenment.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(disponibiliteService.placesParEvent(anyList())).thenReturn(Map.of());

        assertTrue(eventService.listPublished(pageable).getContent().isEmpty());
    }

    @Test
    void listByOrganisateur_organisateurIntrouvable_ResourceNotFound() {
        Pageable pageable = PageRequest.of(0, 10);
        when(organisateurRepository.existsById(orgaId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> eventService.listByOrganisateur(orgaId, pageable));

        verify(eventRepository, never()).findByOrganisateurId(any(), any());
    }

    @Test
    void listByOrganisateur_nominal_retourneSesEvents() {
        Pageable pageable = PageRequest.of(0, 10);
        Event e1 = OneEvent(StatusEvenment.DRAFT, orgaId);
        EventResponse r1 = mock(EventResponse.class);
        when(organisateurRepository.existsById(orgaId)).thenReturn(true);
        when(eventRepository.findByOrganisateurId(orgaId, pageable)).thenReturn(new PageImpl<>(List.of(e1), pageable, 1));
        when(disponibiliteService.placesParEvent(anyList())).thenReturn(Map.of(eventId, 3));
        when(eventMapper.toResponse(e1, 3)).thenReturn(r1);

        Page<EventResponse> result = eventService.listByOrganisateur(orgaId, pageable);

        assertEquals(1, result.getContent().size());
        assertSame(r1, result.getContent().get(0));
    }

    // =====================================================================
    // methodes utilitaires (evitent de repeter le meme code dans plusieurs tests)
    // =====================================================================
    private void updateRefuse(StatusEvenment statut) {
        Event event = OneEvent(statut, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BusinessRuleException.class, () -> eventService.update(eventId, RequestValide(null)));

        verify(eventMapper, never()).updateEntity(any(), any());
    }

    private void publierRefuse(StatusEvenment statut) {
        Event event = OneEvent(statut, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BusinessRuleException.class, () -> eventService.publier(eventId, orgaId));

        assertEquals(statut, event.getStatut()); // statut inchange
    }

    private void getByIdInvisible(StatusEvenment statut) {
        Event event = OneEvent(statut, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ResourceNotFoundException.class, () -> eventService.getById(eventId));

        verifyNoInteractions(eventMapper, disponibiliteService);
    }
}
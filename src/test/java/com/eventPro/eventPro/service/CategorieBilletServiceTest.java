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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT) // pas d'erreur si un stub n'est pas utilise dans un test
class CategorieBilletServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private CategorieBilletMapper categorieBilletMapper;
    @Mock
    private CategorieBilletRepository categorieBilletRepository;
    @Mock
    private DisponibiliteService disponibiliteService;

    @InjectMocks
    private CategorieBilletSercice service; // (renomme en CategorieBilletService si tu corriges la faute)

    private final UUID eventId = UUID.randomUUID();
    private final UUID orgaId = UUID.randomUUID();
    private final UUID categorieId = UUID.randomUUID();

    // ---------- petits helpers (comme OneUser() avant) ----------
    private Event OneEvent(StatusEvenment statut, UUID ownerId) {
        Organisateur orga = Organisateur.builder().id(ownerId).build();
        return Event.builder().id(eventId).statut(statut).organisateur(orga).build();
    }

    private CategorieBillet OneCategorie(Event event, String nom) {
        return CategorieBillet.builder().id(categorieId).nom(nom).event(event)
                .prixUnitaire(new BigDecimal("100.00")).quantiteTotale(10L).quotaMaxParClient(4).build();
    }

    private CategorieBilletRequest OneRequest(String nom, int quantite, int quota, UUID owner) {
        CategorieBilletRequest r = mock(CategorieBilletRequest.class);
        when(r.nom()).thenReturn(nom);
        when(r.quantiteTotale()).thenReturn(quantite);
        when(r.quotaMaxParClient()).thenReturn(quota);
        when(r.organisateurId()).thenReturn(owner);
        return r;
    }

    private CategorieBilletRequest RequestValide() {
        return OneRequest("VIP", 10, 4, orgaId);
    }

    // =====================================================================
    // create
    // =====================================================================
    @Test
    void create_eventDraft_lieLaCategorieALEvent_etPlacesEgalesALaQuantite() {
        createNominal(StatusEvenment.DRAFT);
    }

    @Test
    void create_eventPublished_lieLaCategorieALEvent_etPlacesEgalesALaQuantite() {
        createNominal(StatusEvenment.PUBLISHED);
    }

    @Test
    void create_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.create(eventId, RequestValide()));

        assertTrue(ex.getMessage().contains(eventId.toString()));
        verify(categorieBilletRepository, never()).save(any());
    }

    @Test
    void create_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.DRAFT, UUID.randomUUID()); // un autre organisateur
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ForbiddenOperationException.class, () -> service.create(eventId, RequestValide()));

        verify(categorieBilletRepository, never()).save(any());
    }

    @Test
    void create_eventCancelled_BusinessRule() {
        Event event = OneEvent(StatusEvenment.CANCELLED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, RequestValide()));

        verify(categorieBilletRepository, never()).save(any());
    }

    @Test
    void create_eventCompleted_BusinessRule() {
        Event event = OneEvent(StatusEvenment.COMPLETED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, RequestValide()));

        verify(categorieBilletRepository, never()).save(any());
    }

    @Test
    void create_quotaSuperieurALaQuantite_BusinessRule_sansVerifierLeNom() {
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // quota 10 > quantite 5
        assertThrows(BusinessRuleException.class, () -> service.create(eventId, OneRequest("VIP", 5, 10, orgaId)));

        verify(categorieBilletRepository, never()).existsByEventIdAndNomIgnoreCase(any(), anyString());
        verify(categorieBilletRepository, never()).save(any());
    }

    @Test
    void create_nomDejaUtilisePourCetEvent_BusinessRule() {
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId, "VIP")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.create(eventId, RequestValide()));

        verify(categorieBilletRepository, never()).save(any());
    }

    // =====================================================================
    // listByEvent
    // =====================================================================
    @Test
    void listByEvent_eventIntrouvable_ResourceNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.listByEvent(eventId));
    }

    @Test
    void listByEvent_eventDraft_estInvisible_404() {
        listInvisible(StatusEvenment.DRAFT);
    }

    @Test
    void listByEvent_eventCancelled_estInvisible_404() {
        listInvisible(StatusEvenment.CANCELLED);
    }

    @Test
    void listByEvent_eventCompleted_estInvisible_404() {
        listInvisible(StatusEvenment.COMPLETED);
    }

    @Test
    void listByEvent_nominal_retourneChaqueCategorieAvecSesPlaces() {
        // ARRANGE : 2 categories avec des ids differents
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        CategorieBillet c1 = CategorieBillet.builder().id(id1).nom("VIP").build();
        CategorieBillet c2 = CategorieBillet.builder().id(id2).nom("Standard").build();
        List<CategorieBillet> categories = List.of(c1, c2);
        CategorieBilletResponse r1 = mock(CategorieBilletResponse.class);
        CategorieBilletResponse r2 = mock(CategorieBilletResponse.class);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(categorieBilletRepository.findByEventId(eventId)).thenReturn(categories);
        when(disponibiliteService.placesParCategorie(eventId, categories)).thenReturn(Map.of(id1, 8, id2, 0));
        when(categorieBilletMapper.toResponse(c1, 8)).thenReturn(r1);
        when(categorieBilletMapper.toResponse(c2, 0)).thenReturn(r2);

        // ACT
        List<CategorieBilletResponse> result = service.listByEvent(eventId);

        // ASSERT
        assertEquals(2, result.size());
        assertSame(r1, result.get(0));
        assertSame(r2, result.get(1));
    }

    @Test
    void listByEvent_aucuneCategorie_listeVide() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(categorieBilletRepository.findByEventId(eventId)).thenReturn(List.of());
        when(disponibiliteService.placesParCategorie(eventId, List.of())).thenReturn(Map.of());

        assertTrue(service.listByEvent(eventId).isEmpty());
    }

    // =====================================================================
    // update
    // =====================================================================
    @Test
    void update_categorieIntrouvable_ResourceNotFound() {
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.update(categorieId, RequestValide()));

        assertTrue(ex.getMessage().contains(categorieId.toString()));
    }

    @Test
    void update_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.DRAFT, UUID.randomUUID());
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));

        assertThrows(ForbiddenOperationException.class, () -> service.update(categorieId, RequestValide()));

        verify(categorieBilletMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_eventCancelled_BusinessRule() {
        Event event = OneEvent(StatusEvenment.CANCELLED, orgaId);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));

        assertThrows(BusinessRuleException.class, () -> service.update(categorieId, RequestValide()));

        verify(categorieBilletMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_eventCompleted_BusinessRule() {
        Event event = OneEvent(StatusEvenment.COMPLETED, orgaId);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));

        assertThrows(BusinessRuleException.class, () -> service.update(categorieId, RequestValide()));

        verify(categorieBilletMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_quotaSuperieurALaQuantite_BusinessRule() {
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));

        assertThrows(BusinessRuleException.class, () -> service.update(categorieId, OneRequest("VIP", 5, 10, orgaId)));

        verify(categorieBilletMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_nomChangeEtDejaPris_BusinessRule() {
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));
        when(categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId, "Standard")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.update(categorieId, OneRequest("Standard", 10, 4, orgaId)));

        verify(categorieBilletMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_nomChangeEtLibre_metAJourEtRetourneLesPlaces() {
        // ARRANGE
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        CategorieBillet categorie = OneCategorie(event, "VIP");
        CategorieBilletRequest request = OneRequest("Premium", 10, 4, orgaId);
        CategorieBilletResponse response = mock(CategorieBilletResponse.class);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie));
        when(categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId, "Premium")).thenReturn(false);
        when(disponibiliteService.placesParCategorie(eventId, List.of(categorie))).thenReturn(Map.of(categorieId, 3));
        when(categorieBilletMapper.toResponse(categorie, 3)).thenReturn(response);

        // ACT
        CategorieBilletResponse result = service.update(categorieId, request);

        // ASSERT
        assertSame(response, result);
        verify(categorieBilletMapper).updateEntity(request, categorie);
    }

    @Test
    void update_memeNomAvecCasseDifferente_neVerifiePasLUnicite() {
        // ARRANGE : "VIP" -> "vip" = meme nom, donc pas de test d'unicite
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        CategorieBillet categorie = OneCategorie(event, "VIP");
        CategorieBilletRequest request = OneRequest("vip", 10, 4, orgaId);
        CategorieBilletResponse response = mock(CategorieBilletResponse.class);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie));
        when(disponibiliteService.placesParCategorie(eventId, List.of(categorie))).thenReturn(Map.of(categorieId, 3));
        when(categorieBilletMapper.toResponse(categorie, 3)).thenReturn(response);

        // ACT
        CategorieBilletResponse result = service.update(categorieId, request);

        // ASSERT
        assertSame(response, result);
        verify(categorieBilletRepository, never()).existsByEventIdAndNomIgnoreCase(any(), anyString());
        verify(categorieBilletMapper).updateEntity(request, categorie);
    }

    // =====================================================================
    // delete
    // =====================================================================
    @Test
    void delete_categorieIntrouvable_ResourceNotFound() {
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(categorieId, orgaId));

        verify(categorieBilletRepository, never()).delete(any());
    }

    @Test
    void delete_pasLOrganisateur_Forbidden() {
        Event event = OneEvent(StatusEvenment.DRAFT, UUID.randomUUID());
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));

        assertThrows(ForbiddenOperationException.class, () -> service.delete(categorieId, orgaId));

        verify(categorieBilletRepository, never()).delete(any());
    }

    @Test
    void delete_eventCancelled_BusinessRule() {
        Event event = OneEvent(StatusEvenment.CANCELLED, orgaId);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));

        assertThrows(BusinessRuleException.class, () -> service.delete(categorieId, orgaId));

        verify(categorieBilletRepository, never()).delete(any());
    }

    @Test
    void delete_eventCompleted_BusinessRule() {
        Event event = OneEvent(StatusEvenment.COMPLETED, orgaId);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));

        assertThrows(BusinessRuleException.class, () -> service.delete(categorieId, orgaId));

        verify(categorieBilletRepository, never()).delete(any());
    }

    @Test
    void delete_derniereCategorieDUnEventPublie_BusinessRule() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(OneCategorie(event, "VIP")));
        when(categorieBilletRepository.countByEventId(eventId)).thenReturn(1L); // il n'en reste qu'une

        assertThrows(BusinessRuleException.class, () -> service.delete(categorieId, orgaId));

        verify(categorieBilletRepository, never()).delete(any());
    }

    @Test
    void delete_eventPublieAvecPlusieursCategories_supprime() {
        Event event = OneEvent(StatusEvenment.PUBLISHED, orgaId);
        CategorieBillet categorie = OneCategorie(event, "VIP");
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie));
        when(categorieBilletRepository.countByEventId(eventId)).thenReturn(2L);

        service.delete(categorieId, orgaId);

        verify(categorieBilletRepository).delete(categorie);
    }

    @Test
    void delete_eventDraftAvecUneSeuleCategorie_supprimeSansCompter() {
        Event event = OneEvent(StatusEvenment.DRAFT, orgaId);
        CategorieBillet categorie = OneCategorie(event, "VIP");
        when(categorieBilletRepository.findById(categorieId)).thenReturn(Optional.of(categorie));

        service.delete(categorieId, orgaId);

        verify(categorieBilletRepository).delete(categorie);
        verify(categorieBilletRepository, never()).countByEventId(any()); // la regle ne concerne que les events publies
    }

    // =====================================================================
    // methodes utilitaires (evitent de repeter le meme code dans plusieurs tests)
    // =====================================================================
    private void createNominal(StatusEvenment statut) {
        // ARRANGE
        Event event = OneEvent(statut, orgaId);
        CategorieBilletRequest request = RequestValide();
        CategorieBillet categorie = CategorieBillet.builder().nom("VIP").build();   // ce que renvoie le mapper
        CategorieBillet saved = OneCategorie(event, "VIP");                         // ce que renvoie save
        CategorieBilletResponse response = mock(CategorieBilletResponse.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(categorieBilletRepository.existsByEventIdAndNomIgnoreCase(eventId, "VIP")).thenReturn(false);
        when(categorieBilletMapper.toEntity(request)).thenReturn(categorie);
        when(categorieBilletRepository.save(categorie)).thenReturn(saved);
        when(categorieBilletMapper.toResponse(saved, 10)).thenReturn(response); // 10 = quantiteTotale de saved

        // ACT
        CategorieBilletResponse result = service.create(eventId, request);

        // ASSERT
        assertSame(response, result);
        assertSame(event, categorie.getEvent()); // la categorie est liee a l'event
    }

    private void listInvisible(StatusEvenment statut) {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(OneEvent(statut, orgaId)));

        assertThrows(ResourceNotFoundException.class, () -> service.listByEvent(eventId));

        verify(categorieBilletRepository, never()).findByEventId(any());
    }
}
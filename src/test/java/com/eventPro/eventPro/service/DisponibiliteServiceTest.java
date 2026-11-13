package com.eventPro.eventPro.service;

import com.eventPro.eventPro.model.CategorieBillet;
import com.eventPro.eventPro.repository.CategorieBilletRepository;
import com.eventPro.eventPro.repository.LigneReservationRepository;
import com.eventPro.eventPro.repository.projection.CleQuantite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
@MockitoSettings(strictness = Strictness.LENIENT)
class DisponibiliteServiceTest {

    @Mock
    private LigneReservationRepository ligneReservationRepository;
    @Mock
    private CategorieBilletRepository categorieBilletRepository;

    @InjectMocks
    private DisponibiliteService service;

    private final UUID eventId = UUID.randomUUID();

    // ---------- petit helper (comme OneUser() avant) ----------
    private CategorieBillet OneCategorie(UUID id, long quantiteTotale) {
        return CategorieBillet.builder().id(id).quantiteTotale(quantiteTotale).build();
    }

    // =====================================================================
    // placesParCategorie
    // =====================================================================
    @Test
    void placesParCategorie_soustraitLesReservationsActives_etPlafonneAZero() {
        // ARRANGE : 10 places - 4 prises = 6 ; 3 places - 5 prises = -2 -> 0
        UUID availableId = UUID.randomUUID();
        UUID soldOutId = UUID.randomUUID();
        CategorieBillet available = OneCategorie(availableId, 10L);
        CategorieBillet soldOut = OneCategorie(soldOutId, 3L);
        when(ligneReservationRepository.sumQuantitesActivesParCategorie(eq(eventId), any()))
                .thenReturn(List.of(new CleQuantite(availableId, 4L), new CleQuantite(soldOutId, 5L)));

        // ACT
        Map<UUID, Integer> places = service.placesParCategorie(eventId, List.of(available, soldOut));

        // ASSERT
        assertEquals(6, places.get(availableId));
        assertEquals(0, places.get(soldOutId)); // jamais negatif
    }

    @Test
    void placesParCategorie_aucuneReservation_toutesLesPlacesSontDisponibles() {
        UUID id = UUID.randomUUID();
        when(ligneReservationRepository.sumQuantitesActivesParCategorie(eq(eventId), any()))
                .thenReturn(List.of());

        Map<UUID, Integer> places = service.placesParCategorie(eventId, List.of(OneCategorie(id, 10L)));

        assertEquals(10, places.get(id));
    }

    @Test
    void placesParCategorie_toutPris_retourneZero() {
        // exactement egal : 10 - 10 = 0
        UUID id = UUID.randomUUID();
        when(ligneReservationRepository.sumQuantitesActivesParCategorie(eq(eventId), any()))
                .thenReturn(List.of(new CleQuantite(id, 10L)));

        Map<UUID, Integer> places = service.placesParCategorie(eventId, List.of(OneCategorie(id, 10L)));

        assertEquals(0, places.get(id));
    }

    @Test
    void placesParCategorie_reservationsPourUneAutreCategorie_neChangentPasCelleCi() {
        // la requete retourne des ventes pour une categorie qui n'est pas dans la liste demandee
        UUID id = UUID.randomUUID();
        UUID autreId = UUID.randomUUID();
        when(ligneReservationRepository.sumQuantitesActivesParCategorie(eq(eventId), any()))
                .thenReturn(List.of(new CleQuantite(autreId, 7L)));

        Map<UUID, Integer> places = service.placesParCategorie(eventId, List.of(OneCategorie(id, 10L)));

        assertEquals(1, places.size());
        assertEquals(10, places.get(id));
    }

    @Test
    void placesParCategorie_listeVide_retourneUneMapVide() {
        when(ligneReservationRepository.sumQuantitesActivesParCategorie(eq(eventId), any()))
                .thenReturn(List.of());

        Map<UUID, Integer> places = service.placesParCategorie(eventId, List.of());

        assertTrue(places.isEmpty());
    }

    // =====================================================================
    // placesParEvent
    // =====================================================================
    @Test
    void placesParEvent_entreeVide_retourneUneMapVide_sansAucuneRequete() {
        assertEquals(Map.of(), service.placesParEvent(Set.of()));

        verifyNoInteractions(categorieBilletRepository, ligneReservationRepository);
    }

    @Test
    void placesParEvent_soustraitLesReservationsActives() {
        // ARRANGE : capacite 100, 30 prises
        when(categorieBilletRepository.sumQuantiteTotaleParEvent(any()))
                .thenReturn(List.of(new CleQuantite(eventId, 100L)));
        when(ligneReservationRepository.sumQuantitesActivesParEvent(any(), any()))
                .thenReturn(List.of(new CleQuantite(eventId, 30L)));

        // ACT
        Map<UUID, Integer> places = service.placesParEvent(List.of(eventId));

        // ASSERT
        assertEquals(70, places.get(eventId));
    }

    @Test
    void placesParEvent_aucuneReservation_toutEstDisponible() {
        when(categorieBilletRepository.sumQuantiteTotaleParEvent(any()))
                .thenReturn(List.of(new CleQuantite(eventId, 100L)));
        when(ligneReservationRepository.sumQuantitesActivesParEvent(any(), any()))
                .thenReturn(List.of());

        Map<UUID, Integer> places = service.placesParEvent(List.of(eventId));

        assertEquals(100, places.get(eventId));
    }

    @Test
    void placesParEvent_plusDeReservationsQueDeCapacite_plafonneAZero() {
        when(categorieBilletRepository.sumQuantiteTotaleParEvent(any()))
                .thenReturn(List.of(new CleQuantite(eventId, 10L)));
        when(ligneReservationRepository.sumQuantitesActivesParEvent(any(), any()))
                .thenReturn(List.of(new CleQuantite(eventId, 15L)));

        Map<UUID, Integer> places = service.placesParEvent(List.of(eventId));

        assertEquals(0, places.get(eventId));
    }

    @Test
    void placesParEvent_eventSansCategorie_zeroPlace() {
        // l'event n'apparait pas dans les totaux : capacite 0
        when(categorieBilletRepository.sumQuantiteTotaleParEvent(any())).thenReturn(List.of());
        when(ligneReservationRepository.sumQuantitesActivesParEvent(any(), any())).thenReturn(List.of());

        Map<UUID, Integer> places = service.placesParEvent(List.of(eventId));

        assertEquals(0, places.get(eventId));
    }

    @Test
    void placesParEvent_plusieursEvents_chacunALuiMeme() {
        // ARRANGE : event1 100-30=70 ; event2 50-0=50
        UUID event1 = UUID.randomUUID();
        UUID event2 = UUID.randomUUID();
        when(categorieBilletRepository.sumQuantiteTotaleParEvent(any()))
                .thenReturn(List.of(new CleQuantite(event1, 100L), new CleQuantite(event2, 50L)));
        when(ligneReservationRepository.sumQuantitesActivesParEvent(any(), any()))
                .thenReturn(List.of(new CleQuantite(event1, 30L)));

        // ACT
        Map<UUID, Integer> places = service.placesParEvent(List.of(event1, event2));

        // ASSERT
        assertEquals(2, places.size());
        assertEquals(70, places.get(event1));
        assertEquals(50, places.get(event2));
    }
}
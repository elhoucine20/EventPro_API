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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisponibiliteServiceTest {

    @Mock LigneReservationRepository ligneReservationRepository;
    @Mock CategorieBilletRepository categorieBilletRepository;
    @InjectMocks DisponibiliteService service;

    @Test
    void placesParCategorieSubtractsActiveReservationsAndClampsAtZero() {
        UUID eventId = UUID.randomUUID();
        UUID soldOutId = UUID.randomUUID();
        CategorieBillet available = CategorieBillet.builder().id(UUID.randomUUID()).quantiteTotale(10L).build();
        CategorieBillet soldOut = CategorieBillet.builder().id(soldOutId).quantiteTotale(3L).build();
        when(ligneReservationRepository.sumQuantitesActivesParCategorie(eq(eventId), any()))
                .thenReturn(List.of(new CleQuantite(available.getId(), 4L), new CleQuantite(soldOutId, 5L)));

        Map<UUID, Integer> places = service.placesParCategorie(eventId, List.of(available, soldOut));

        assertEquals(6, places.get(available.getId()));
        assertEquals(0, places.get(soldOutId));
    }

    @Test
    void placesParEventReturnsEmptyMapWithoutQueryForEmptyInput() {
        assertEquals(Map.of(), service.placesParEvent(Set.of()));

        verifyNoInteractions(categorieBilletRepository, ligneReservationRepository);
    }
}

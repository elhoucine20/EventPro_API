package com.eventPro.eventPro.mapper.ticket;

import com.eventPro.eventPro.dto.ticket.TicketResponse;
import com.eventPro.eventPro.model.Billet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    @Mapping(target = "categorieNom", source = "ligneReservation.categorieBillet.nom")
    @Mapping(target = "eventId", source = "ligneReservation.reservation.event.id")
    @Mapping(target = "eventTitre", source = "ligneReservation.reservation.event.titre")
    @Mapping(target = "eventLieu", source = "ligneReservation.reservation.event.lieu")
    @Mapping(target = "eventDateDebut", source = "ligneReservation.reservation.event.dateDebut")
    @Mapping(target = "reservationReference", source = "ligneReservation.reservation.reference")
    @Mapping(target = "porteur", source = "ligneReservation.reservation.client.name")
    TicketResponse toResponse(Billet billet);

}

package com.eventPro.eventPro.mapper.reservation;

import com.eventPro.eventPro.dto.ligneReservation.LigneReservationResponse;
import com.eventPro.eventPro.dto.reservation.ReservationResponse;
import com.eventPro.eventPro.model.LigneReservation;
import com.eventPro.eventPro.model.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    @Mapping(target = "promoCode", source = "promotion.code")
    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "eventTitre", source = "event.titre")
    @Mapping(target = "clientId", source = "client.id")
    ReservationResponse toResponse(Reservation reservation);


    @Mapping(target = "categorieBilletId", source = "categorieBillet.id")
    @Mapping(target = "categorieNom", source = "categorieBillet.nom")
    LigneReservationResponse toLigneResponse(LigneReservation ligne);

}

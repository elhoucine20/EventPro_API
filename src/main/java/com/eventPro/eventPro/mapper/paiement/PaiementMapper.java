package com.eventPro.eventPro.mapper.paiement;


import com.eventPro.eventPro.dto.paiement.PaiementResponse;
import com.eventPro.eventPro.model.Paiement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaiementMapper {

    @Mapping(target = "reservationId", source = "reservation.id")
    @Mapping(target = "reservationStatut", source = "reservation.statut")
    PaiementResponse toResponse(Paiement paiement);

}

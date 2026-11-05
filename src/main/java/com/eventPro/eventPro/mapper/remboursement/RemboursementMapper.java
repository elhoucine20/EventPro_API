package com.eventPro.eventPro.mapper.remboursement;

import com.eventPro.eventPro.dto.remboursement.RemboursementResponse;
import com.eventPro.eventPro.model.Remboursement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RemboursementMapper {

    @Mapping(target = "reservationId", source = "paiement.reservation.id")
    @Mapping(target = "reservationStatut", source = "paiement.reservation.statut")
    @Mapping(target = "paiementReference", source = "paiement.reference")
    RemboursementResponse toResponse(Remboursement remboursement);
}
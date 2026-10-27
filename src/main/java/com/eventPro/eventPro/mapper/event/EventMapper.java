package com.eventPro.eventPro.mapper.event;

import com.eventPro.eventPro.dto.event.EventRequest;
import com.eventPro.eventPro.dto.event.EventResponse;
import com.eventPro.eventPro.model.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;


@Mapper(componentModel = "spring")  // indique a spring cette class definie des regles de conversion
       // componentModel demande a mapStruct de generer l'implimentaion
public interface EventMapper {

    @Mapping(target = "id",ignore = true)
    @Mapping(target = "statut",ignore = true)
    @Mapping(target = "organisateur",ignore = true)
    @Mapping(target = "categoriesBillet",ignore = true)
    @Mapping(target = "promotions", ignore = true)
    Event toEntity(EventRequest request);


    @Mapping(target = "organisateurId", source = "organisateur.id")
    @Mapping(target = "nomOrganisation", source = "organisateur.nomOrganisation")
    EventResponse toResponse(Event event);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "organisateur", ignore = true)
    @Mapping(target = "categoriesBillet", ignore = true)
    @Mapping(target = "promotions", ignore = true)
    void updateEntity(EventRequest request, @MappingTarget Event event); // indique a spring de update cette objet

}

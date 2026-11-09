package com.eventPro.eventPro.mapper.categorie;


import com.eventPro.eventPro.dto.categorie.CategorieBilletRequest;
import com.eventPro.eventPro.dto.categorie.CategorieBilletResponse;
import com.eventPro.eventPro.model.CategorieBillet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategorieBilletMapper {

    @Mapping(target = "id",ignore = true)
    @Mapping(target = "event",ignore = true)
    CategorieBillet toEntity(CategorieBilletRequest request);

    @Mapping(target = "eventId",source = "event.id")
    @Mapping(target = "placesDisponibles", source = "placesDisponibles")
    CategorieBilletResponse toResponse(CategorieBillet categorieBillet,Integer placesDisponibles);

    @Mapping(target = "id",ignore = true)
    @Mapping(target = "event",ignore = true)
    void updateEntity(CategorieBilletRequest request, @MappingTarget CategorieBillet categorieBillet);
}

package com.eventPro.eventPro.mapper.promotion;

import com.eventPro.eventPro.dto.promotion.PromotionRequest;
import com.eventPro.eventPro.dto.promotion.PromotionResponse;
import com.eventPro.eventPro.model.Promotion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PromotionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", ignore = true)
    @Mapping(target = "nombreUtilisations", ignore = true)
    Promotion toEntity(PromotionRequest request);

    @Mapping(target = "eventId", source = "event.id")
    PromotionResponse toResponse(Promotion promotion);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", ignore = true)
    @Mapping(target = "nombreUtilisations", ignore = true)
    void updateEntity(PromotionRequest request, @MappingTarget Promotion promotion);

}

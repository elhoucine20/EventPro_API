package com.eventPro.eventPro.mapper.user;
import com.eventPro.eventPro.dto.user.RegisterClientRequest;
import com.eventPro.eventPro.dto.user.RegisterOrganisateurRequest;
import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.model.Client;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", source = "hashPassword")
    @Mapping(target = "role", constant = "CLIENT")
    @Mapping(target = "reservations", ignore = true)
    Client toClient(RegisterClientRequest req, String hashPassword, String salt);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", source = "hashPassword")
    @Mapping(target = "role", constant = "ORGANISATEUR")
    @Mapping(target = "events", ignore = true)
    Organisateur toOrganisateur(RegisterOrganisateurRequest req, String hashPassword, String salt);

    //Organisateur toOrganisateur(RegisterClientRequest request, String hashPassword, String salt);

    UserResponse toResponse(User user);
}
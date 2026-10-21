package com.eventPro.eventPro.mapper.user;

import com.eventPro.eventPro.dto.user.RegisterClientRequest;
import com.eventPro.eventPro.dto.user.RegisterOrganisateurRequest;
import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.model.Client;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.model.User;
import com.eventPro.eventPro.model.enums.RoleUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class UserMapperrr {

    public Client toClient(RegisterClientRequest request,String hashPassword,String salt){
        return Client.builder()
                .name(request.name())
                .email(request.email())
                .password(hashPassword)
                .salt(salt)
                .role(RoleUser.CLIENT)
                .phone(request.phone())
                .build();
    }

    public Organisateur toOrganisateur(RegisterOrganisateurRequest request,String hashPassword,String salt){
        return Organisateur.builder()
                .name(request.name())
                .email(request.email())
                .password(hashPassword)
                .salt(salt)
                .role(RoleUser.ORGANISATEUR)
                .nomOrganisation(request.nomOrganisation())
                .build();
    }
    public UserResponse toResponse(User user){
        return new UserResponse(user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole());
    }

    public List<UserResponse> mapToAll(List<User> users){
        List<UserResponse> userss = new ArrayList<>();
        users.forEach(u->userss.add(toResponse(u)));
        return userss;
    }
}

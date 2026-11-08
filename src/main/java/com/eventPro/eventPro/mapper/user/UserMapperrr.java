package com.eventPro.eventPro.mapper.user;

import com.eventPro.eventPro.dto.admin.RegisterAdminRequest;
import com.eventPro.eventPro.dto.agent.RegisterAgentRequest;
import com.eventPro.eventPro.dto.user.RegisterClientRequest;
import com.eventPro.eventPro.dto.user.RegisterOrganisateurRequest;
import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.model.*;
import com.eventPro.eventPro.model.enums.RoleUser;
import org.aspectj.weaver.loadtime.Agent;
import org.mapstruct.Mapping;
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


    public AgentControle toAgent(RegisterAgentRequest request,String hashPassword,String salt){
        return AgentControle.builder()
                .name(request.name())
                .email(request.email())
                .password(hashPassword)
                .salt(salt)
                .role(RoleUser.AGENT_CONTROLE)
                .build();
    }


    public Administrateur toAdmin(RegisterAdminRequest request, String hashPassword, String salt){
        return Administrateur.builder()
                .name(request.name())
                .email(request.email())
                .password(hashPassword)
                .salt(salt)
                .role(RoleUser.ADMIN)
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

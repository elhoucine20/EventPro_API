package com.eventPro.eventPro.dto.user;

import com.eventPro.eventPro.model.enums.RoleUser;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UserResponse(
        UUID id,
        String name,
        String email,
        RoleUser role
) {}

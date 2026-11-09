package com.eventPro.eventPro.globalException;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String message,
        Map<String,String> errors
) {
}

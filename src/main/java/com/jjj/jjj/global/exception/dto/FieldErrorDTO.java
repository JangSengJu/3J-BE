package com.jjj.jjj.global.exception.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record FieldErrorDTO<T>(
        int status,
        String code,
        String message,
        List<FieldDTO> details,
        Instant timestamp
) {
}
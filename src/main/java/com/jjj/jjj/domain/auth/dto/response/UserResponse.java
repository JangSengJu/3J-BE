package com.jjj.jjj.domain.auth.dto.response;

import com.jjj.jjj.domain.auth.entity.Member;

import java.time.LocalDateTime;

public record UserResponse(
        String id,
        LocalDateTime createdAt
) {
    public static UserResponse from(Member member) {
        return new UserResponse(member.getUsername(), member.getCreatedAt());
    }
}

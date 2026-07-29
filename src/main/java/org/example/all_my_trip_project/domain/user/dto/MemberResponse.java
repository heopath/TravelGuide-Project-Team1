package org.example.all_my_trip_project.domain.user.dto;

public record MemberResponse(
        Long userId,
        String email,
        String nickname,
        String role,
        String status
) {
}
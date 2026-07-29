package org.example.all_my_trip_project.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long userId;
    private String email;
    private String passwordHash;
    private String nickname;
    private String role;
    private String status;
    // PostgreSQL TIMESTAMPTZ의 UTC offset을 보존하기 위한 타입이며 DTO 필드명은 기존과 동일하다.
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}

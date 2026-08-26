package org.example.all_my_trip_project.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 링크에 담긴 토큰과 새 비밀번호. */
public record PasswordResetConfirmRequest(
        @NotBlank(message = "링크가 올바르지 않습니다.")
        @Size(max = 200)
        String token,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.")
        String newPassword
) {}

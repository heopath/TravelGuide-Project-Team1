package org.example.all_my_trip_project.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberPasswordChangeRequest(

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(
                min = 8,
                message = "새 비밀번호는 8자 이상이어야 합니다."
        )
        String newPassword

) {
}
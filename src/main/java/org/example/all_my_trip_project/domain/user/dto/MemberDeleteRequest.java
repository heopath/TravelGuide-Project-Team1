package org.example.all_my_trip_project.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberDeleteRequest(

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String password

) {
}
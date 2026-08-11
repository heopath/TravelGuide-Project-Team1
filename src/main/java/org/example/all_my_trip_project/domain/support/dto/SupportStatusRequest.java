package org.example.all_my_trip_project.domain.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SupportStatusRequest(
        @NotBlank(message = "처리 상태를 선택해 주세요.")
        @Pattern(regexp = "OPEN|IN_PROGRESS|ANSWERED|CLOSED", message = "올바른 문의 상태가 아닙니다.")
        String status
) {
}

package org.example.all_my_trip_project.domain.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportReplyRequest(
        @NotBlank(message = "답변 내용을 입력해 주세요.")
        @Size(max = 3000, message = "답변은 3000자 이내로 입력해 주세요.")
        String content
) {
}

package org.example.all_my_trip_project.domain.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportChatMessageRequest(

        /** {@code content}가 VARCHAR(2000)이다. 넘치면 저장 단계에서 터지므로 여기서 막는다. */
        @NotBlank(message = "보낼 내용을 입력해 주세요.")
        @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다.")
        String content
) {}

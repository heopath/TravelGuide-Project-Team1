package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TicketValidationRequest(

        /** 손님이 보여준 입장 코드. 서버는 해시로만 대조하고 원문을 저장하지 않는다. */
        @NotBlank
        @Size(max = 200)
        String token,

        /** 어디서 검표했는지. 지금은 관리자 화면뿐이라 비우면 {@code ADMIN_WEB}이다. */
        @Pattern(regexp = "ADMIN_WEB|MOCK_SCANNER|API",
                message = "지원하지 않는 검표 경로입니다.")
        String channel,

        /** 스캐너를 붙일 때 어느 기기였는지 구분하려고 둔다. 지금은 비워 보낸다. */
        @Size(max = 100)
        String deviceId
) {}

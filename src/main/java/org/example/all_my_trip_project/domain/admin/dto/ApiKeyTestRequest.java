package org.example.all_my_trip_project.domain.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * 연결 테스트 요청.
 *
 * <p>{@code apiKey}가 비어 있으면 지금 쓰이는 키로 확인한다. 저장 전 검증과 "지금 키가 아직
 * 살아 있나" 확인을 같은 버튼으로 처리하기 위해 비워 보내는 것을 허용한다.
 */
public record ApiKeyTestRequest(
        @Size(max = 500, message = "API 키가 너무 깁니다.")
        String apiKey
) {
}

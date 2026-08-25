package org.example.all_my_trip_project.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 키 저장·연결 테스트 요청 본문.
 *
 * <p>형식을 정규식으로 강하게 묶지 않았다. 발급처가 키 형식을 바꾸면(OpenAI는 실제로 몇 번
 * 바꿨다) 멀쩡한 키를 화면이 거부하게 된다. 공백만 걸러내고, 진짜 유효한지는 연결 테스트로
 * 확인한다.
 */
public record ApiKeyUpdateRequest(
        @NotBlank(message = "API 키를 입력해 주세요.")
        @Size(max = 500, message = "API 키가 너무 깁니다.")
        String apiKey
) {
}

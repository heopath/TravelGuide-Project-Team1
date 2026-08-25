package org.example.all_my_trip_project.domain.admin.dto;

/**
 * 연결 테스트 결과.
 *
 * @param statusCode 외부 API가 돌려준 HTTP 상태. 호출 자체를 못 했으면 0
 */
public record ApiKeyTestResultDTO(boolean valid, int statusCode, String message) {
}

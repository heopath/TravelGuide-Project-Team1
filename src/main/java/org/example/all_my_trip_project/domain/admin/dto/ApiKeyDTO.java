package org.example.all_my_trip_project.domain.admin.dto;

import java.time.OffsetDateTime;

/**
 * 관리자 화면에 보낼 API 키 한 줄.
 *
 * <p>키 전체 값은 절대 담지 않는다. {@code maskedValue}는 {@code sk-••••4a2f}처럼 앞뒤 몇 글자만
 * 남긴 문자열이다. 화면에 전체 값을 띄우면 캡처 한 장, 어깨너머 한 번으로 유출된다.
 *
 * @param source     지금 실제로 쓰이는 값의 출처. {@code STORED}=관리자 저장값, {@code ENV}=환경변수,
 *                   {@code NONE}=어디에도 없음
 */
public record ApiKeyDTO(
        String name,
        String label,
        String description,
        String maskedValue,
        String source,
        OffsetDateTime updatedAt,
        Long updatedBy
) {
    public static final String SOURCE_STORED = "STORED";
    public static final String SOURCE_ENV = "ENV";
    public static final String SOURCE_NONE = "NONE";
}

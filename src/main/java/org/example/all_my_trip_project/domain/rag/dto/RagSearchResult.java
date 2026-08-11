package org.example.all_my_trip_project.domain.rag.dto;

/**
 * AI 프롬프트에 전달할 공개 장소 지식 검색 결과다.
 * 사용자 식별 정보나 원본 DB 엔티티는 이 DTO에 포함하지 않는다.
 */
public record RagSearchResult(
        String source,
        String content,
        Long placeId,
        String placeName,
        String category,
        String address,
        String placeUrl
) {
    public RagSearchResult(String source, String content) {
        this(source, content, null, null, null, null, null);
    }
}

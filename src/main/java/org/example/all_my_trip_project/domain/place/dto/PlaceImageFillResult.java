package org.example.all_my_trip_project.domain.place.dto;

/**
 * 고른 장소들의 대표 이미지를 채운 결과.
 *
 * @param requested 요청한 장소 수
 * @param filled    실제로 이미지를 넣은 장소 수
 * @param skipped   이미 이미지가 있거나 좌표가 없어 건너뛴 장소 수
 * @param notFound  관광정보에서 같은 장소를 찾지 못한 장소 수
 */
public record PlaceImageFillResult(
        int requested,
        int filled,
        int skipped,
        int notFound
) {
}

package org.example.all_my_trip_project.domain.place.dto;

/**
 * 대표 이미지 채우기 한 묶음의 결과.
 *
 * @param scanned   이번에 확인한 장소 수
 * @param filled    실제로 이미지를 넣은 장소 수
 * @param nextAfter 다음 요청에 넘길 커서. scanned가 0이면 직전 값을 그대로 돌려준다.
 * @param done      더 볼 장소가 없으면 true
 * @param remaining 아직 대표 이미지가 없는 장소 수(커서와 무관한 전체 기준)
 */
public record PlaceImageBackfillResult(
        int scanned,
        int filled,
        long nextAfter,
        boolean done,
        long remaining
) {
}

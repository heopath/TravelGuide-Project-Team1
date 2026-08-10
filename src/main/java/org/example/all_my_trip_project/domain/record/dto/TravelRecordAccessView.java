package org.example.all_my_trip_project.domain.record.dto;

import org.example.all_my_trip_project.domain.record.type.RecordVisibility;

/**
 * record 도메인 밖에서 여행 기록의 존재·소유자·공개 범위만 필요할 때 쓰는 읽기 전용 결과다.
 * Entity를 노출하지 않고 다른 도메인이 필요한 최소 필드만 전달한다.
 */
public record TravelRecordAccessView(
        Long travelRecordId,
        Long tripId,
        Long ownerUserId,
        RecordVisibility visibility
) {
}

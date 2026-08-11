package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.TravelRecordAccessView;
import org.example.all_my_trip_project.global.exception.BusinessException;

/**
 * 다른 도메인이 여행 기록의 존재·공개 여부를 확인할 때 쓰는 공개 계약이다. record 도메인의
 * Repository나 Entity를 직접 참조하지 않고 이 계약만 의존하게 해서, "누가 이 기록을 볼 수 있는가"에
 * 대한 판단을 record 도메인 하나로 모은다.
 */
public interface TravelRecordAccessGuard {

    /**
     * 존재하지 않거나 소프트 삭제됐거나, PRIVATE인데 조회자가 소유자가 아니면 {@link BusinessException}을 던진다.
     * 통과하면 신고 등 후속 처리에 필요한 최소 정보만 반환한다.
     */
    TravelRecordAccessView requireAccessibleRecord(Long viewerUserId, Long travelRecordId);
}

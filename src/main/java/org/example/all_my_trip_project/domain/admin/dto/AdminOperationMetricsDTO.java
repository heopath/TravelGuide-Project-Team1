package org.example.all_my_trip_project.domain.admin.dto;

import java.time.OffsetDateTime;

/**
 * 관리자 운영 지표.
 *
 * <p>화면의 네 칸에 그대로 대응한다. 값이 없을 수 있는 칸은 {@code null}로 내려보내고
 * 화면이 빈 자리로 표시한다. 0으로 채우면 "없음"으로 읽히는데, 실제로는 잴 수 없었다는
 * 뜻인 경우가 있어 정반대로 오해된다.
 *
 * @param lowStockThreshold {@link #lowStockSlots}를 셀 때 쓴 기준 수량. 화면이 "5개 이하"처럼
 *                          기준을 함께 밝히려면 필요하다. 기준을 숨기면 숫자만 보고 판단하게 된다.
 * @param errorRate         서버 오류율. 아직 집계된 요청이 없으면 {@code null}이다.
 */
public record AdminOperationMetricsDTO(
        long todayReservations,
        long openInquiries,
        long lowStockSlots,
        int lowStockThreshold,
        Double errorRate,
        OffsetDateTime collectedAt
) {}

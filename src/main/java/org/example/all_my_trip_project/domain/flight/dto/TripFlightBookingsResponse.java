package org.example.all_my_trip_project.domain.flight.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 우측 예약 현황 패널과 `내 예약` 탭이 쓰는 통합 조회 결과.
 *
 * @param airSelectedTotal 선택된 구간들의 스냅샷 운임 합계. 미선택 구간은 빠져 있다.
 * @param airIsEstimate    왕복 두 구간 모두 자가 신고되기 전까지 참
 * @param airPriceSource   두 구간의 출처가 다르면 {@code MIXED}. 선택이 없으면 null.
 *
 * <p><b>화면의 `예상 총액`은 이 값만으로 만들 수 없다.</b> 미선택 구간의 추천가는
 * 그 시점 검색 결과에서 나오는데 서버는 그 결과를 갖고 있지 않다.
 * 그래서 상태 파생(status/airIsEstimate/progress)은 전부 서버가 내려주고,
 * 미선택 구간의 추천가만 화면이 자기 검색 결과에서 채운다.
 */
public record TripFlightBookingsResponse(
        List<FlightBookingLegResponse> legs,
        BigDecimal airSelectedTotal,
        boolean airIsEstimate,
        boolean airDone,
        String airPriceSource,
        Progress progress,
        List<UnresolvedOutboundClick> unresolvedClicks
) {
    public static final String MIXED = "MIXED";

    /** 항공·숙소·티켓 3개 중 몇 개가 끝났는지. 숙소·티켓은 미구현이라 항상 0이다. */
    public record Progress(int done, int total) {}

    /**
     * 복귀 감지를 놓쳐 결과가 비어 있는 이탈 이력.
     * 다음 방문에 "이 항공편, 예약하셨나요?" 배너로 다시 물어볼 대상이다.
     */
    public record UnresolvedOutboundClick(Long clickId, int leg, String offerId) {}
}

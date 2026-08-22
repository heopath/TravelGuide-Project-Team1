package org.example.all_my_trip_project.domain.trip.type;

import java.time.LocalDate;

public enum TripStatus {
    DRAFT,
    CONFIRMED,
    COMPLETED,
    CANCELLED;

    /**
     * 여행이 끝났는지 판단한다.
     *
     * <p>{@code COMPLETED}로 상태를 바꾸는 코드가 없어 날짜가 지나도 {@code CONFIRMED}에
     * 머문다. 그래서 상태만 보면 여행 기록도 장소 리뷰도 영원히 쓸 수 없다. 상태를 바꾸는
     * 배치를 두는 대신, 읽는 시점에 종료일로 판단한다.
     *
     * <p>확정한 여행만 센다. 초안({@code DRAFT})은 아직 여행이라 하기 이르고,
     * 취소({@code CANCELLED})는 다녀오지 않은 여행이다.
     *
     * <p>종료일 당일은 아직 여행 중으로 본다. 다음 날부터 끝난 것으로 친다.
     */
    public static boolean isFinished(String status, LocalDate endDate, LocalDate today) {
        if (COMPLETED.name().equals(status)) return true;
        if (!CONFIRMED.name().equals(status)) return false;
        return endDate != null && today != null && endDate.isBefore(today);
    }
}

package org.example.all_my_trip_project.domain.trip.type;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행이 끝났는지 판단하는 규칙.
 *
 * <p>COMPLETED로 상태를 바꾸는 코드가 없어 상태만 보면 다녀온 뒤에도 CONFIRMED에
 * 머문다. 그래서 종료일로도 판단한다. 이 규칙이 흔들리면 여행 기록과 장소 리뷰가
 * 통째로 막히거나, 반대로 다녀오지도 않은 여행에 기록을 쓸 수 있게 된다.
 */
class TripStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 22);

    @Test
    void 상태가_COMPLETED면_날짜와_무관하게_끝난_여행이다() {
        assertThat(TripStatus.isFinished("COMPLETED", LocalDate.of(2030, 1, 1), TODAY)).isTrue();
    }

    @Test
    void 확정한_여행은_종료일_다음날부터_끝난_여행이다() {
        assertThat(TripStatus.isFinished("CONFIRMED", LocalDate.of(2026, 8, 21), TODAY)).isTrue();
    }

    @Test
    void 종료일_당일은_아직_여행_중이다() {
        assertThat(TripStatus.isFinished("CONFIRMED", TODAY, TODAY)).isFalse();
    }

    @Test
    void 아직_다녀오지_않은_여행은_끝나지_않았다() {
        assertThat(TripStatus.isFinished("CONFIRMED", LocalDate.of(2026, 9, 10), TODAY)).isFalse();
    }

    @Test
    void 초안은_날짜가_지나도_끝난_여행이_아니다() {
        assertThat(TripStatus.isFinished("DRAFT", LocalDate.of(2026, 8, 1), TODAY)).isFalse();
    }

    @Test
    void 취소한_여행은_날짜가_지나도_끝난_여행이_아니다() {
        assertThat(TripStatus.isFinished("CANCELLED", LocalDate.of(2026, 8, 1), TODAY)).isFalse();
    }

    @Test
    void 종료일이_없으면_끝난_여행으로_보지_않는다() {
        assertThat(TripStatus.isFinished("CONFIRMED", null, TODAY)).isFalse();
    }
}

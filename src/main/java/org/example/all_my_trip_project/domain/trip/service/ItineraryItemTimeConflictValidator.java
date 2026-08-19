package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Component
final class ItineraryItemTimeConflictValidator {

    private static final int DEFAULT_AI_STAY_MINUTES = 120;
    private static final int DAY_MINUTES = 24 * 60;

    /**
     * AI 추천은 종료 시각이 자정과 같아지는 경우도 다음 DAY로 넘어가는 것으로 본다.
     * 일정 화면의 추가 가능 여부와 서버 저장 검증을 동일한 정책으로 유지한다.
     */
    boolean exceedsAiDayBoundary(ItineraryItemDTO candidate) {
        if (candidate == null || candidate.getStartTime() == null) {
            return false;
        }
        int startMinutes = candidate.getStartTime().getHour() * 60 + candidate.getStartTime().getMinute();
        return startMinutes + DEFAULT_AI_STAY_MINUTES >= DAY_MINUTES;
    }

    boolean hasConflict(ItineraryItemDTO candidate, List<ItineraryItemDTO> existingItems) {
        if (candidate.getStartTime() == null || existingItems == null) {
            return false;
        }

        TimeRange candidateRange = rangeOf(candidate);
        return existingItems.stream()
                .filter(existing -> existing.getStartTime() != null)
                .map(this::rangeOf)
                .anyMatch(candidateRange::overlaps);
    }

    /**
     * 수정 중인 항목 자신은 비교 대상에서 제외한다.
     * 새 시간 범위가 같은 DAY의 다른 일정과 겹치는지만 검사한다.
     */
    boolean hasConflictExcludingSameItem(ItineraryItemDTO candidate, List<ItineraryItemDTO> existingItems) {
        if (candidate.getStartTime() == null || existingItems == null) {
            return false;
        }

        TimeRange candidateRange = rangeOf(candidate);
        return existingItems.stream()
                .filter(existing -> candidate.getItineraryItemId() == null
                        || !java.util.Objects.equals(
                        existing.getItineraryItemId(), candidate.getItineraryItemId()))
                .filter(existing -> existing.getStartTime() != null)
                .map(this::rangeOf)
                .anyMatch(candidateRange::overlaps);
    }

    private TimeRange rangeOf(ItineraryItemDTO item) {
        LocalTime start = item.getStartTime();
        LocalTime end = item.getEndTime();
        if (end == null || !end.isAfter(start)) {
            end = start.plusMinutes(DEFAULT_AI_STAY_MINUTES);
            /*
             * 기본 체류 시간을 더하다 자정을 넘으면 그날 끝으로 자른다.
             *
             * 넘긴 채로 두면 아래 overlaps가 "자정을 넘는 범위"로 보고 검사를 통째로 포기한다.
             * 그러면 22:30 추천이 기존 22:00~23:00 일정과 겹치는데도 통과한다. 넘기는 쪽은
             * AI가 아니라 여기서 더하는 2시간이라, 자르는 것도 여기가 맞다.
             */
            if (!end.isAfter(start)) {
                end = LocalTime.MAX;
            }
        }
        return new TimeRange(start.toSecondOfDay(), end.toSecondOfDay());
    }

    private record TimeRange(int startSecond, int endSecond) {
        private boolean overlaps(TimeRange other) {
            // 자정을 넘는 범위는 현재 AI 추천 시간 형식에서 만들지 않으므로 충돌로 처리하지 않는다.
            if (endSecond <= startSecond || other.endSecond <= other.startSecond) {
                return false;
            }
            return startSecond < other.endSecond && other.startSecond < endSecond;
        }
    }
}

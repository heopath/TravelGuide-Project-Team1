package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Component
final class ItineraryItemTimeConflictValidator {

    private static final int DEFAULT_AI_STAY_MINUTES = 120;

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

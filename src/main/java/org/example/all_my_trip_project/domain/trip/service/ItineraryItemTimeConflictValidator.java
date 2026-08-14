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

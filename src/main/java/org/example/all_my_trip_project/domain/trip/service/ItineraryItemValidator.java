package org.example.all_my_trip_project.domain.trip.service;

import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!ui")
final class ItineraryItemValidator {
    public void validate(ItineraryItemDTO item) {
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new IllegalArgumentException("일정 제목은 필수입니다.");
        }
        if (item.getStartTime() != null && item.getEndTime() != null
                && item.getEndTime().isBefore(item.getStartTime())) {
            throw new IllegalArgumentException("일정 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
        if (item.getEstimatedCost() != null && item.getEstimatedCost().signum() < 0) {
            throw new IllegalArgumentException("예상 비용은 음수일 수 없습니다.");
        }
    }
}

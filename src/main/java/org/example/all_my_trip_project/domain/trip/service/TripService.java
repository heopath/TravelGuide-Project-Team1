package org.example.all_my_trip_project.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.time.temporal.ChronoUnit;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {
    private static final int MAX_TRAVELERS = 20;
    private static final int MAX_TRIP_DAYS = 30;
    private final TripDAO tripDAO;

    @Transactional
    public Long create(TripDTO trip) {
        validateDates(trip);
        applyGeneratedTitle(trip, null);
        tripDAO.insert(trip);
        return trip.getTripId();
    }

    public TripDTO get(Long tripId) {
        return tripDAO.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행을 찾을 수 없습니다. tripId=" + tripId));
    }

    public List<TripDTO> getByUser(Long userId) {
        return tripDAO.findByUserId(userId);
    }

    @Transactional
    public void update(TripDTO trip) {
        validateDates(trip);
        TripDTO current = get(trip.getTripId());
        applyGeneratedTitle(trip, current);
        if (tripDAO.update(trip) == 0) {
            throw new IllegalArgumentException("수정할 여행을 찾을 수 없습니다. tripId=" + trip.getTripId());
        }
    }

    @Transactional
    public void delete(Long tripId) {
        if (tripDAO.softDelete(tripId) == 0) {
            throw new IllegalArgumentException("삭제할 여행을 찾을 수 없습니다. tripId=" + tripId);
        }
    }

    private void validateDates(TripDTO trip) {
        if (trip.getStartDate() != null && trip.getEndDate() != null
                && trip.getEndDate().isBefore(trip.getStartDate())) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (trip.getStartDate() != null && trip.getEndDate() != null
                && ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1 > MAX_TRIP_DAYS) {
            throw new IllegalArgumentException("여행 기간은 시작일과 종료일을 포함해 최대 30일까지 가능합니다.");
        }
        if (trip.getCompanionCount() != null
                && (trip.getCompanionCount() < 1 || trip.getCompanionCount() > MAX_TRAVELERS)) {
            throw new IllegalArgumentException("여행 인원은 1명부터 최대 20명까지 가능합니다.");
        }
    }

    private void applyGeneratedTitle(TripDTO incoming, TripDTO current) {
        String incomingTitle = trimToNull(incoming.getTitle());
        String generatedForIncoming = generatedTitle(incoming.getDestinationName(), incoming.getStartDate());
        if (current == null) {
            incoming.setTitle(incomingTitle == null ? generatedForIncoming : incomingTitle);
            return;
        }

        String currentTitle = trimToNull(current.getTitle());
        String generatedForCurrent = generatedTitle(current.getDestinationName(), current.getStartDate());
        String legacyGeneratedTitle = trimToNull(current.getDestinationName()) == null
                ? "나의 여행"
                : trimToNull(current.getDestinationName()) + " 여행";
        boolean currentTitleIsGenerated = currentTitle == null
                || Objects.equals(currentTitle, generatedForCurrent)
                || Objects.equals(currentTitle, legacyGeneratedTitle);
        if (currentTitleIsGenerated) {
            incoming.setTitle(generatedForIncoming);
        } else if (incomingTitle == null) {
            incoming.setTitle(currentTitle);
        } else {
            incoming.setTitle(incomingTitle);
        }
    }

    private String generatedTitle(String destinationName, java.time.LocalDate startDate) {
        String destination = trimToNull(destinationName);
        if (destination == null) return "나의 여행";
        return startDate == null ? destination + " 여행" : startDate.getMonthValue() + "월의 " + destination + " 여행";
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}

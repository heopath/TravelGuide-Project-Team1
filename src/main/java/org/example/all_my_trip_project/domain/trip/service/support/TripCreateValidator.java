package org.example.all_my_trip_project.domain.trip.service.support;

import org.example.all_my_trip_project.domain.trip.dto.TripCreateRequest;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.example.all_my_trip_project.domain.trip.type.CompanionType;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

@Component
public class TripCreateValidator {

    public int validate(TripCreateRequest request) {
        if (request == null
                || request.destinationName() == null
                || request.destinationName().isBlank()
                || request.startDate() == null
                || request.endDate() == null
                || request.companionType() == null) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_REQUEST);
        }
        if (request.destinationName().trim().length() > TripPolicy.MAX_DESTINATION_NAME_LENGTH
                || (request.title() != null
                && request.title().trim().length() > TripPolicy.MAX_TITLE_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_REQUEST);
        }

        int dayCount = validatePeriod(request);
        validateCompanion(request);
        validateBudget(request);
        return dayCount;
    }

    private int validatePeriod(TripCreateRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_PERIOD);
        }

        long dayCount = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (dayCount > TripPolicy.MAX_TRIP_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_PERIOD);
        }
        return Math.toIntExact(dayCount);
    }

    private void validateCompanion(TripCreateRequest request) {
        Integer companionCount = request.companionCount();
        if (companionCount == null
                || companionCount < TripPolicy.MIN_COMPANION_COUNT
                || companionCount > TripPolicy.MAX_COMPANION_COUNT
                || (request.companionType() == CompanionType.SOLO && companionCount != 1)) {
            throw new BusinessException(ErrorCode.INVALID_COMPANION_COUNT);
        }
    }

    private void validateBudget(TripCreateRequest request) {
        if (request.budgetAmount() != null && request.budgetAmount().signum() < 0) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_REQUEST);
        }
    }
}

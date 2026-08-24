package org.example.all_my_trip_project.domain.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** AI guide prompt only. It never creates or changes trip data. */
public record AiGuideContext(Trip trip, List<Preference> preferences) {
    public AiGuideContext {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
    }

    public record Trip(Long tripId, String title, String destinationName,
                       LocalDate startDate, LocalDate endDate, String companionType,
                       Integer companionCount, String purpose, BigDecimal budgetAmount,
                       String currencyCode, String transportPreference, String foodPreference,
                       String pace, String accommodationStyle, List<Day> days) {
        public Trip {
            days = days == null ? List.of() : List.copyOf(days);
        }
    }

    public record Day(Integer dayNumber, LocalDate tripDate, String title, String memo, List<Item> items) {
        public Day {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record Item(Long placeId, String title, LocalTime startTime, LocalTime endTime,
                       String itemType, String memo) {
    }

    public record Preference(String code, String name, Short score) {
    }
}

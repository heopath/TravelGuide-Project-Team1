package org.example.all_my_trip_project.domain.trip.policy;

public final class TripPolicy {
    public static final int MAX_TRIP_DAYS = 30;
    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_DESTINATION_NAME_LENGTH = 150;
    public static final int MIN_COMPANION_COUNT = 1;
    public static final int MAX_COMPANION_COUNT = 20;
    public static final int MAX_ITINERARY_ITEMS_PER_DAY = 100;
    public static final String DEFAULT_CURRENCY_CODE = "KRW";
    public static final String DEFAULT_TITLE_SUFFIX = " 여행";

    private TripPolicy() {
    }
}

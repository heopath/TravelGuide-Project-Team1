package org.example.all_my_trip_project.domain.record.policy;

public final class RecordPolicy {
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_IMAGE_COUNT = 20;
    public static final int MAX_IMAGE_URL_LENGTH = 1000;
    public static final int MAX_IMAGE_ALT_TEXT_LENGTH = 255;

    private RecordPolicy() {
    }
}

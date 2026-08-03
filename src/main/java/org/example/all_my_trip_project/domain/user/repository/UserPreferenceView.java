package org.example.all_my_trip_project.domain.user.repository;

public interface UserPreferenceView {

    Short getTravelStyleId();

    String getCode();

    String getName();

    Short getPreferenceScore();

    String getSource();
}

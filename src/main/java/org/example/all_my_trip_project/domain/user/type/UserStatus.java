package org.example.all_my_trip_project.domain.user.type;

public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN;

    public boolean matches(String value) {
        return name().equals(value);
    }
}

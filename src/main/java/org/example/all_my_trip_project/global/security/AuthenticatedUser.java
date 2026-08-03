package org.example.all_my_trip_project.global.security;

import java.io.Serializable;

public record AuthenticatedUser(
        Long userId,
        String email,
        String role
) implements Serializable {
}
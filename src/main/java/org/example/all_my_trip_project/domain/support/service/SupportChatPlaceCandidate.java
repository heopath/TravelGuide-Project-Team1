package org.example.all_my_trip_project.domain.support.service;

public record SupportChatPlaceCandidate(
        Long placeId,
        String name,
        String category,
        String address,
        String description
) {}

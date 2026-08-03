package org.example.all_my_trip_project.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiGuideService {
    private final AiModelClient aiModelClient;

    public AiGuideResponse generate(AiGuideRequest request, boolean simulateServerError) {
        if (simulateServerError) {
            throw new IllegalStateException("AI mock server error");
        }
        return aiModelClient.generate(request);
    }
}

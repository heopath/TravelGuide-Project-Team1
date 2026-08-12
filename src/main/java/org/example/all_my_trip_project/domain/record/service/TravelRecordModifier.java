package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.UpdateTravelRecordRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!ui")
class TravelRecordModifier {

    void update(TravelRecordEntity record, UpdateTravelRecordRequest request) {
        record.updateContent(
                request.title().trim(),
                request.content(),
                request.rating(),
                request.visibility()
        );
    }
}

package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!ui")
class TravelRecordRemover {

    void remove(TravelRecordEntity record) {
        record.softDelete();
    }
}

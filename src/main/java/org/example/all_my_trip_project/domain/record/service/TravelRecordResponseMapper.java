package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.TravelRecordImageResponse;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordResponse;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordImageEntity;
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!ui")
class TravelRecordResponseMapper {

    TravelRecordResponse toResponse(TravelRecordEntity record, List<TravelRecordImageEntity> images) {
        return new TravelRecordResponse(
                record.getTravelRecordId(),
                record.getTripId(),
                record.getUserId(),
                record.getTitle(),
                record.getContent(),
                record.getRating(),
                RecordVisibility.valueOf(record.getVisibility()),
                images.stream().map(this::toImageResponse).toList(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private TravelRecordImageResponse toImageResponse(TravelRecordImageEntity image) {
        return new TravelRecordImageResponse(
                image.getTravelRecordImageId(),
                image.getImageUrl(),
                image.getAltText(),
                image.getSortOrder(),
                image.getCover()
        );
    }
}

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
                resolveImageUrl(image),
                image.getAltText(),
                image.getSortOrder(),
                image.getCover()
        );
    }

    /* S3 객체 키를 화면에 직접 노출하지 않는다. 공개 기록은 누구나, 비공개 기록은 소유자만
       아래 이미지 API를 통과하므로 버킷을 public으로 열 필요가 없다. 기존 외부 URL은 호환을 위해 유지한다. */
    private String resolveImageUrl(TravelRecordImageEntity image) {
        if (image.getImageUrl().startsWith("s3://")) {
            return "/api/v1/travel-records/images/" + image.getTravelRecordImageId() + "/content";
        }
        return image.getImageUrl();
    }
}

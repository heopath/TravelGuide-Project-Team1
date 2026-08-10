package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.policy.RecordPolicy;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!ui")
class TravelRecordValidator {

    void validateImages(ReplaceRecordImagesRequest request) {
        List<ReplaceRecordImagesRequest.ImageItem> images = request.images();
        if (images.size() > RecordPolicy.MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
        long coverCount = images.stream()
                .filter(ReplaceRecordImagesRequest.ImageItem::cover)
                .count();
        if (coverCount > 1) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
    }
}

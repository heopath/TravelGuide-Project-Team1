package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.policy.RecordPolicy;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelRecordValidatorTest {

    private final TravelRecordValidator validator = new TravelRecordValidator();

    @Test
    void acceptsZeroCoverImages() {
        assertThatCode(() -> validator.validateImages(request(item(false), item(false))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsExactlyOneCoverImage() {
        assertThatCode(() -> validator.validateImages(request(item(true), item(false))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMoreThanOneCoverImage() {
        assertThatThrownBy(() -> validator.validateImages(request(item(true), item(true))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_RECORD_REQUEST);
    }

    @Test
    void rejectsMoreImagesThanThePolicyLimit() {
        List<ReplaceRecordImagesRequest.ImageItem> tooMany = new ArrayList<>();
        for (int i = 0; i < RecordPolicy.MAX_IMAGE_COUNT + 1; i++) {
            tooMany.add(item(false));
        }

        assertThatThrownBy(() -> validator.validateImages(new ReplaceRecordImagesRequest(tooMany)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_RECORD_REQUEST);
    }

    private ReplaceRecordImagesRequest request(ReplaceRecordImagesRequest.ImageItem... items) {
        return new ReplaceRecordImagesRequest(List.of(items));
    }

    private ReplaceRecordImagesRequest.ImageItem item(boolean cover) {
        return new ReplaceRecordImagesRequest.ImageItem("https://example.com/a.jpg", "alt", cover);
    }
}

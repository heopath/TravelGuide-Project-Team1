package org.example.all_my_trip_project.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordImageEntity;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordImageRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("!ui")
@RequiredArgsConstructor
class TravelRecordImageReplacer {

    private static final Pattern IMAGE_CONTENT_URL = Pattern.compile("(?:^|.*/)api/v1/travel-records/images/(\\d+)/content(?:$|[?#].*)");

    private final TravelRecordImageRepository travelRecordImageRepository;

    void replace(Long travelRecordId, ReplaceRecordImagesRequest request) {
        /* 응답에는 S3 키 대신 접근 제어용 이미지 API URL을 준다. 화면이 그 URL을 다시 보내도
           삭제 전에 기존 행에서 실제 S3 참조를 복원해 두어, 다음 교체 뒤에 끊어진 옛 이미지 ID를
           저장하지 않는다. */
        Map<Long, String> previousUrls = travelRecordImageRepository
                .findByTravelRecordIdOrderBySortOrderAsc(travelRecordId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        TravelRecordImageEntity::getTravelRecordImageId,
                        TravelRecordImageEntity::getImageUrl
                ));
        travelRecordImageRepository.deleteByTravelRecordId(travelRecordId);

        List<ReplaceRecordImagesRequest.ImageItem> items = request.images();
        List<TravelRecordImageEntity> images = new ArrayList<>(items.size());
        // 화면에 표시할 순서는 요청 목록 순서 그대로이며, DB CHECK(sort_order > 0)에 맞춰 1부터 부여한다.
        for (int index = 0; index < items.size(); index++) {
            ReplaceRecordImagesRequest.ImageItem item = items.get(index);
            images.add(TravelRecordImageEntity.of(
                    travelRecordId,
                    resolveStoredUrl(item.imageUrl(), previousUrls),
                    item.altText() == null ? null : item.altText().trim(),
                    index + 1,
                    item.cover()
            ));
        }

        travelRecordImageRepository.saveAll(images);
    }

    private String resolveStoredUrl(String imageUrl, Map<Long, String> previousUrls) {
        String normalized = imageUrl.trim();
        Matcher matcher = IMAGE_CONTENT_URL.matcher(normalized);
        if (!matcher.matches()) return normalized;
        Long previousImageId = Long.valueOf(matcher.group(1));
        String storedUrl = previousUrls.get(previousImageId);
        return storedUrl == null ? normalized : storedUrl;
    }
}

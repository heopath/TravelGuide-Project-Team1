package org.example.all_my_trip_project.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordImageEntity;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordImageRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("!ui")
@RequiredArgsConstructor
class TravelRecordImageReplacer {

    private final TravelRecordImageRepository travelRecordImageRepository;
    private final TravelRecordImageStorage imageStorage;

    void replace(Long travelRecordId, ReplaceRecordImagesRequest request) {
        List<String> previousUrls = travelRecordImageRepository
                .findByTravelRecordIdOrderBySortOrderAsc(travelRecordId).stream()
                .map(TravelRecordImageEntity::getImageUrl)
                .toList();
        travelRecordImageRepository.deleteByTravelRecordId(travelRecordId);

        List<ReplaceRecordImagesRequest.ImageItem> items = request.images();
        List<TravelRecordImageEntity> images = new ArrayList<>(items.size());
        // 화면에 표시할 순서는 요청 목록 순서 그대로이며, DB CHECK(sort_order > 0)에 맞춰 1부터 부여한다.
        for (int index = 0; index < items.size(); index++) {
            ReplaceRecordImagesRequest.ImageItem item = items.get(index);
            images.add(TravelRecordImageEntity.of(
                    travelRecordId,
                    item.imageUrl().trim(),
                    item.altText() == null ? null : item.altText().trim(),
                    index + 1,
                    item.cover()
            ));
        }

        travelRecordImageRepository.saveAll(images);

        List<String> nextUrls = items.stream()
                .map(ReplaceRecordImagesRequest.ImageItem::imageUrl)
                .map(String::trim)
                .toList();
        previousUrls.stream()
                .filter(url -> !nextUrls.contains(url))
                .forEach(url -> imageStorage.deleteManaged(travelRecordId, url));
    }
}
